import type { FastifyInstance } from 'fastify'

interface CreateConversationBody {
  type: 'direct' | 'group'
  userId?: string      // direct: the other user
  name?: string        // group: chat name
  memberIds?: string[] // group: other participants
}

interface SendMessageBody {
  content: string
  type?: 'text'
  reply_to_id?: string
}

interface MessageParams {
  id: string
}

interface MessagesQuery {
  limit?: number
  before?: string // message UUID cursor
}

export async function conversationRoutes(app: FastifyInstance) {
  app.addHook('onRequest', app.authenticate)

  // ----------------------------------------------------------------
  // GET /api/conversations  — my conversation list with last message
  // ----------------------------------------------------------------
  app.get('/', async (request) => {
    const { userId } = request.user

    const { rows } = await app.pg.query<{
      id: string
      name: string | null
      type: string
      avatar_url: string | null
      updated_at: string
      last_message_content: string | null
      last_message_type: string | null
      last_message_at: string | null
      last_message_user_id: string | null
      unread_count: string
    }>(
      `SELECT
         c.id, c.name, c.type, c.avatar_url, c.updated_at,
         lm.content  AS last_message_content,
         lm.type     AS last_message_type,
         lm.created_at AS last_message_at,
         lm.user_id  AS last_message_user_id,
         (
           SELECT COUNT(*) FROM messages m2
           WHERE m2.conversation_id = c.id
             AND m2.deleted_at IS NULL
             AND m2.created_at > COALESCE(cp.last_read_at, '1970-01-01'::timestamptz)
             AND m2.user_id != $1
         ) AS unread_count
       FROM conversations c
       JOIN conversation_participants cp
         ON cp.conversation_id = c.id AND cp.user_id = $1
       LEFT JOIN LATERAL (
         SELECT content, type, created_at, user_id
         FROM messages
         WHERE conversation_id = c.id AND deleted_at IS NULL
         ORDER BY created_at DESC LIMIT 1
       ) lm ON true
       ORDER BY COALESCE(lm.created_at, c.updated_at) DESC`,
      [userId],
    )

    return rows.map((r) => ({
      id: r.id,
      name: r.name,
      type: r.type,
      avatarUrl: r.avatar_url,
      updatedAt: r.updated_at,
      lastMessage: r.last_message_at
        ? {
            content: r.last_message_content,
            type: r.last_message_type,
            sentAt: r.last_message_at,
            userId: r.last_message_user_id,
          }
        : null,
      unreadCount: Number(r.unread_count),
    }))
  })

  // ----------------------------------------------------------------
  // POST /api/conversations  — create direct or group chat
  // ----------------------------------------------------------------
  app.post<{ Body: CreateConversationBody }>('/', async (request, reply) => {
    const { userId } = request.user
    const { type, userId: otherUserId, name, memberIds } = request.body

    if (type === 'direct') {
      if (!otherUserId) return reply.status(400).send({ error: 'userId required for direct chat' })

      // Return existing direct conversation if already exists
      const existing = await app.pg.query<{ id: string }>(
        `SELECT c.id FROM conversations c
         JOIN conversation_participants cp1 ON cp1.conversation_id = c.id AND cp1.user_id = $1
         JOIN conversation_participants cp2 ON cp2.conversation_id = c.id AND cp2.user_id = $2
         WHERE c.type = 'direct'
         LIMIT 1`,
        [userId, otherUserId],
      )
      if (existing.rows[0]) return { id: existing.rows[0].id, existed: true }

      const client = await app.pg.connect()
      try {
        await client.query('BEGIN')
        const { rows } = await client.query<{ id: string }>(
          `INSERT INTO conversations (type, created_by) VALUES ('direct', $1) RETURNING id`,
          [userId],
        )
        const convId = rows[0].id
        await client.query(
          `INSERT INTO conversation_participants (conversation_id, user_id) VALUES ($1,$2),($1,$3)`,
          [convId, userId, otherUserId],
        )
        await client.query('COMMIT')
        reply.status(201)
        return { id: convId, existed: false }
      } catch (e) {
        await client.query('ROLLBACK')
        throw e
      } finally {
        client.release()
      }
    }

    // group
    if (!name) return reply.status(400).send({ error: 'name required for group chat' })
    const members = Array.from(new Set([userId, ...(memberIds ?? [])]))

    const client = await app.pg.connect()
    try {
      await client.query('BEGIN')
      const { rows } = await client.query<{ id: string }>(
        `INSERT INTO conversations (type, name, created_by) VALUES ('group', $1, $2) RETURNING id`,
        [name, userId],
      )
      const convId = rows[0].id
      const placeholders = members.map((_, i) => `($1, $${i + 2})`).join(', ')
      await client.query(
        `INSERT INTO conversation_participants (conversation_id, user_id) VALUES ${placeholders}`,
        [convId, ...members],
      )
      // creator is admin
      await client.query(
        `UPDATE conversation_participants SET role='admin' WHERE conversation_id=$1 AND user_id=$2`,
        [convId, userId],
      )
      await client.query('COMMIT')
      reply.status(201)
      return { id: convId, existed: false }
    } catch (e) {
      await client.query('ROLLBACK')
      throw e
    } finally {
      client.release()
    }
  })

  // ----------------------------------------------------------------
  // GET /api/conversations/:id/messages  — history with cursor pagination
  // ----------------------------------------------------------------
  app.get<{ Params: MessageParams; Querystring: MessagesQuery }>(
    '/:id/messages',
    async (request, reply) => {
      const { userId } = request.user
      const { id: convId } = request.params
      const limit = Math.min(Number(request.query.limit) || 50, 100)
      const before = request.query.before ?? null

      // access check
      const access = await app.pg.query(
        `SELECT 1 FROM conversation_participants WHERE conversation_id=$1 AND user_id=$2`,
        [convId, userId],
      )
      if (!access.rows[0]) return reply.status(403).send({ error: 'Forbidden' })

      const { rows } = await app.pg.query<{
        id: string
        content: string | null
        type: string
        created_at: string
        edited_at: string | null
        reply_to_id: string | null
        user_id: string
        username: string
        avatar_url: string | null
      }>(
        `SELECT
           m.id, m.content, m.type, m.created_at, m.edited_at, m.reply_to_id,
           u.id AS user_id, u.username, u.avatar_url
         FROM messages m
         JOIN users u ON u.id = m.user_id
         WHERE m.conversation_id = $1
           AND m.deleted_at IS NULL
           AND ($3::uuid IS NULL OR m.created_at < (
                 SELECT created_at FROM messages WHERE id = $3
               ))
         ORDER BY m.created_at DESC
         LIMIT $2`,
        [convId, limit, before],
      )

      // Mark messages as read
      await app.pg.query(
        `UPDATE conversation_participants SET last_read_at = NOW()
         WHERE conversation_id=$1 AND user_id=$2`,
        [convId, userId],
      )

      return rows.reverse().map((r) => ({
        id: r.id,
        content: r.content,
        type: r.type,
        createdAt: r.created_at,
        editedAt: r.edited_at,
        replyToId: r.reply_to_id,
        sender: { id: r.user_id, username: r.username, avatarUrl: r.avatar_url },
      }))
    },
  )

  // ----------------------------------------------------------------
  // POST /api/conversations/:id/messages  — send a message
  // ----------------------------------------------------------------
  app.post<{ Params: MessageParams; Body: SendMessageBody }>(
    '/:id/messages',
    async (request, reply) => {
      const { userId } = request.user
      const { id: convId } = request.params
      const { content, type = 'text', reply_to_id } = request.body

      if (!content?.trim()) return reply.status(400).send({ error: 'content required' })

      // access check
      const access = await app.pg.query(
        `SELECT 1 FROM conversation_participants WHERE conversation_id=$1 AND user_id=$2`,
        [convId, userId],
      )
      if (!access.rows[0]) return reply.status(403).send({ error: 'Forbidden' })

      const { rows } = await app.pg.query<{
        id: string
        content: string
        type: string
        created_at: string
        reply_to_id: string | null
      }>(
        `INSERT INTO messages (conversation_id, user_id, type, content, reply_to_id)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id, content, type, created_at, reply_to_id`,
        [convId, userId, type, content.trim(), reply_to_id ?? null],
      )

      const msg = rows[0]

      // Update conversation timestamp
      await app.pg.query(`UPDATE conversations SET updated_at=NOW() WHERE id=$1`, [convId])

      const userRow = await app.pg.query<{ username: string; avatar_url: string | null }>(
        `SELECT username, avatar_url FROM users WHERE id=$1`,
        [userId],
      )

      const payload = {
        id: msg.id,
        content: msg.content,
        type: msg.type,
        createdAt: msg.created_at,
        replyToId: msg.reply_to_id,
        sender: {
          id: userId,
          username: userRow.rows[0].username,
          avatarUrl: userRow.rows[0].avatar_url,
        },
        conversationId: convId,
      }

      reply.status(201)
      return payload
    },
  )
}
