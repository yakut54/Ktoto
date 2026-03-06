import type { FastifyInstance } from 'fastify'
import { v4 as uuidv4 } from 'uuid'
import sharp from 'sharp'
import { pushToUser } from '../../services/push.service.js'
import { Readable } from 'stream'

interface CreateConversationBody {
  type: 'direct' | 'group'
  userId?: string      // direct: the other user
  name?: string        // group: chat name
  memberIds?: string[] // group: other participants
}

interface SendMessageBody {
  content?: string
  type?: 'text'
  reply_to_id?: string
  forward_message_id?: string
}

interface MessageParams {
  id: string
}

interface EditMessageParams {
  id: string
  msgId: string
}

interface EditMessageBody {
  content: string
}

interface MessagesQuery {
  limit?: number
  before?: string // message UUID cursor
}

async function streamToBuffer(stream: Readable): Promise<Buffer> {
  const chunks: Buffer[] = []
  for await (const chunk of stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  }
  return Buffer.concat(chunks)
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
      other_username: string | null
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
         ) AS unread_count,
         other.username AS other_username
       FROM conversations c
       JOIN conversation_participants cp
         ON cp.conversation_id = c.id AND cp.user_id = $1
       LEFT JOIN LATERAL (
         SELECT content, type, created_at, user_id
         FROM messages
         WHERE conversation_id = c.id AND deleted_at IS NULL
         ORDER BY created_at DESC LIMIT 1
       ) lm ON true
       LEFT JOIN LATERAL (
         SELECT u.username FROM conversation_participants cp2
         JOIN users u ON u.id = cp2.user_id
         WHERE cp2.conversation_id = c.id AND cp2.user_id != $1
         LIMIT 1
       ) other ON c.type = 'direct'
       ORDER BY COALESCE(lm.created_at, c.updated_at) DESC`,
      [userId],
    )

    return rows.map((r) => ({
      id: r.id,
      name: r.type === 'direct' ? r.other_username : r.name,
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
        att_id: string | null
        att_file_name: string | null
        att_file_size: string | null
        att_mime_type: string | null
        att_file_type: string | null
        att_s3_key: string | null
        att_thumb_key: string | null
        att_duration: string | null
        att_width: number | null
        att_height: number | null
      }>(
        `SELECT
           m.id, m.content, m.type, m.created_at, m.edited_at, m.reply_to_id,
           u.id AS user_id, u.username, u.avatar_url,
           fa.id AS att_id, fa.file_name AS att_file_name,
           fa.file_size_bytes AS att_file_size, fa.mime_type AS att_mime_type,
           fa.file_type AS att_file_type, fa.s3_key AS att_s3_key,
           fa.thumbnail_s3_key AS att_thumb_key,
           fa.duration_seconds AS att_duration,
           fa.image_width AS att_width, fa.image_height AS att_height
         FROM messages m
         JOIN users u ON u.id = m.user_id
         LEFT JOIN file_attachments fa ON fa.message_id = m.id
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

      const result = await Promise.all(
        rows.reverse().map(async (r) => {
          let attachment = null
          if (r.att_id) {
            const url = await app.s3.presignedUrl(r.att_s3_key!)
            const thumbnailUrl = r.att_thumb_key ? await app.s3.presignedUrl(r.att_thumb_key) : null
            attachment = {
              fileName: r.att_file_name,
              fileSize: r.att_file_size ? Number(r.att_file_size) : null,
              mimeType: r.att_mime_type,
              url,
              thumbnailUrl,
              duration: r.att_duration ? Number(r.att_duration) : null,
              width: r.att_width,
              height: r.att_height,
            }
          }
          return {
            id: r.id,
            content: r.content,
            type: r.type,
            createdAt: r.created_at,
            editedAt: r.edited_at,
            replyToId: r.reply_to_id,
            sender: { id: r.user_id, username: r.username, avatarUrl: r.avatar_url },
            conversationId: convId,
            attachment,
          }
        }),
      )

      return result
    },
  )

  // ----------------------------------------------------------------
  // POST /api/conversations/:id/messages  — send a message (text or media)
  // ----------------------------------------------------------------
  app.post<{ Params: MessageParams }>(
    '/:id/messages',
    async (request, reply) => {
      const { userId } = request.user
      const { id: convId } = request.params

      // access check
      const access = await app.pg.query(
        `SELECT 1 FROM conversation_participants WHERE conversation_id=$1 AND user_id=$2`,
        [convId, userId],
      )
      if (!access.rows[0]) return reply.status(403).send({ error: 'Forbidden' })

      let content: string | null = null
      let msgType = 'text'
      let replyToId: string | null = null
      let attachment: {
        fileName: string
        fileSize: number
        mimeType: string
        url: string
        thumbnailUrl: string | null
        duration: number | null
        width: number | null
        height: number | null
      } | null = null

      const contentType = request.headers['content-type'] ?? ''

      if (contentType.includes('multipart/form-data')) {
        // --- multipart upload ---
        const parts = request.parts()
        let fileBuffer: Buffer | null = null
        let fileName = 'file'
        let mimeType = 'application/octet-stream'
        let duration: number | null = null

        for await (const part of parts) {
          app.log.info(`[upload] part: type=${part.type} fieldname=${part.fieldname} ${part.type === 'file' ? `filename=${part.filename} mimetype=${part.mimetype}` : `value=${JSON.stringify(part.value).substring(0, 200)}`}`)
          if (part.type === 'file') {
            fileName = part.filename || 'file'
            mimeType = part.mimetype || 'application/octet-stream'
            fileBuffer = await streamToBuffer(part.file)
            app.log.info(`[upload] file buffered: name=${fileName} mime=${mimeType} size=${fileBuffer.byteLength}`)
          } else if (part.type === 'field' && part.fieldname === 'meta') {
            try {
              const meta = JSON.parse(part.value as string)
              content = meta.content ?? null
              msgType = meta.type ?? 'file'
              replyToId = meta.reply_to_id ?? null
              duration = meta.duration ?? null
              app.log.info(`[upload] meta parsed: msgType=${msgType} duration=${duration} replyToId=${replyToId}`)
            } catch (e) {
              app.log.warn(`[upload] meta parse failed: raw=${JSON.stringify(part.value)} err=${e}`)
            }
          }
        }

        app.log.info(`[upload] after parts: fileBuffer=${fileBuffer ? fileBuffer.byteLength : 'NULL'} msgType=${msgType} mimeType=${mimeType}`)
        if (!fileBuffer) return reply.status(400).send({ error: 'file required' })

        const ext = fileName.split('.').pop() ?? 'bin'
        const s3Key = `${convId}/${uuidv4()}.${ext}`
        await app.s3.upload(s3Key, fileBuffer, mimeType)

        let thumbKey: string | null = null
        let width: number | null = null
        let height: number | null = null

        // Detect type by MIME if meta wasn't parsed (ensures valid file_type for DB CHECK constraint)
        const msgTypeBefore = msgType
        if (mimeType.startsWith('audio/')) {
          msgType = 'voice'
        } else if (mimeType.startsWith('video/')) {
          msgType = 'video'
        } else if (msgType === 'text' || !['image', 'video', 'voice', 'file'].includes(msgType)) {
          msgType = 'file'
        }
        app.log.info(`[upload] type detection: before=${msgTypeBefore} after=${msgType} mime=${mimeType}`)

        // Generate thumbnail for images
        if (msgType === 'image' || mimeType.startsWith('image/')) {
          msgType = 'image'
          try {
            const img = sharp(fileBuffer)
            const meta = await img.metadata()
            width = meta.width ?? null
            height = meta.height ?? null
            const thumbBuffer = await img
              .resize({ width: 320, withoutEnlargement: true })
              .jpeg({ quality: 80 })
              .toBuffer()
            thumbKey = `${s3Key}_thumb.jpg`
            await app.s3.upload(thumbKey, thumbBuffer, 'image/jpeg')
          } catch (err) {
            app.log.warn({ err }, 'Failed to generate thumbnail')
          }
        }

        // Insert message
        const { rows: msgRows } = await app.pg.query<{
          id: string; created_at: string; reply_to_id: string | null
        }>(
          `INSERT INTO messages (conversation_id, user_id, type, content, reply_to_id)
           VALUES ($1, $2, $3, $4, $5)
           RETURNING id, created_at, reply_to_id`,
          [convId, userId, msgType, content, replyToId],
        )
        const msg = msgRows[0]

        // Insert file_attachment
        await app.pg.query(
          `INSERT INTO file_attachments
             (message_id, file_name, file_size_bytes, mime_type, file_type,
              s3_key, thumbnail_s3_key, duration_seconds, image_width, image_height)
           VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
          [
            msg.id, fileName, fileBuffer.byteLength, mimeType, msgType,
            s3Key, thumbKey, duration, width, height,
          ],
        )

        const url = await app.s3.presignedUrl(s3Key)
        const thumbnailUrl = thumbKey ? await app.s3.presignedUrl(thumbKey) : null

        attachment = { fileName, fileSize: fileBuffer.byteLength, mimeType, url, thumbnailUrl, duration, width, height }

        await app.pg.query(`UPDATE conversations SET updated_at=NOW() WHERE id=$1`, [convId])

        const userRow = await app.pg.query<{ username: string; avatar_url: string | null }>(
          `SELECT username, avatar_url FROM users WHERE id=$1`, [userId],
        )

        const payload = {
          id: msg.id,
          content,
          type: msgType,
          createdAt: msg.created_at,
          replyToId: msg.reply_to_id,
          sender: { id: userId, username: userRow.rows[0].username, avatarUrl: userRow.rows[0].avatar_url },
          conversationId: convId,
          attachment,
        }

        const participants = await app.pg.query<{ user_id: string }>(
          `SELECT user_id FROM conversation_participants WHERE conversation_id=$1`, [convId],
        )
        for (const p of participants.rows) {
          app.io.to(`user:${p.user_id}`).emit('new_message', payload)
          if (p.user_id !== userId) {
            const previewContent = msgType === 'image' ? '📷 Фото' :
                                   msgType === 'voice' ? '🎤 Голосовое' :
                                   msgType === 'video' ? '🎬 Видео' : '📎 Файл'
            pushToUser(p.user_id, userRow.rows[0].username, previewContent)
          }
        }

        reply.status(201)
        return payload

      } else {
        // --- regular JSON text message ---
        const body = request.body as SendMessageBody
        content = body.content ?? null
        msgType = body.type ?? 'text'
        replyToId = body.reply_to_id ?? null

        // ── Forward: copy original message to target conversation ──────────────
        if (body.forward_message_id) {
          const origRes = await app.pg.query<{
            content: string | null; msg_type: string
            s3_key: string | null; file_name: string | null; file_size_bytes: number | null
            mime_type: string | null; file_type: string | null; thumb_key: string | null
            duration_seconds: number | null; image_width: number | null; image_height: number | null
          }>(
            `SELECT m.content, m.type AS msg_type,
                    fa.s3_key, fa.file_name, fa.file_size_bytes, fa.mime_type,
                    fa.file_type, fa.thumbnail_s3_key AS thumb_key, fa.duration_seconds,
                    fa.image_width, fa.image_height
             FROM messages m
             LEFT JOIN file_attachments fa ON fa.message_id = m.id
             WHERE m.id = $1 AND m.deleted_at IS NULL`,
            [body.forward_message_id],
          )
          if (!origRes.rows[0]) return reply.status(404).send({ error: 'Original message not found' })
          const orig = origRes.rows[0]

          const { rows: fwdRows } = await app.pg.query<{ id: string; created_at: string }>(
            `INSERT INTO messages (conversation_id, user_id, type, content)
             VALUES ($1, $2, $3, $4) RETURNING id, created_at`,
            [convId, userId, orig.msg_type, orig.content],
          )
          const fwdMsg = fwdRows[0]

          let fwdAttachment = null
          if (orig.s3_key) {
            await app.pg.query(
              `INSERT INTO file_attachments
                 (message_id, file_name, file_size_bytes, mime_type, file_type,
                  s3_key, thumbnail_s3_key, duration_seconds, image_width, image_height)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
              [fwdMsg.id, orig.file_name, orig.file_size_bytes, orig.mime_type, orig.file_type,
               orig.s3_key, orig.thumb_key, orig.duration_seconds, orig.image_width, orig.image_height],
            )
            const url = await app.s3.presignedUrl(orig.s3_key)
            const thumbnailUrl = orig.thumb_key ? await app.s3.presignedUrl(orig.thumb_key) : null
            fwdAttachment = {
              fileName: orig.file_name, fileSize: orig.file_size_bytes, mimeType: orig.mime_type,
              url, thumbnailUrl, duration: orig.duration_seconds,
              width: orig.image_width, height: orig.image_height,
            }
          }

          await app.pg.query(`UPDATE conversations SET updated_at=NOW() WHERE id=$1`, [convId])

          const userRow = await app.pg.query<{ username: string; avatar_url: string | null }>(
            `SELECT username, avatar_url FROM users WHERE id=$1`, [userId],
          )
          const payload = {
            id: fwdMsg.id, content: orig.content, type: orig.msg_type,
            createdAt: fwdMsg.created_at, replyToId: null,
            sender: { id: userId, username: userRow.rows[0].username, avatarUrl: userRow.rows[0].avatar_url },
            conversationId: convId, attachment: fwdAttachment,
          }
          const participants = await app.pg.query<{ user_id: string }>(
            `SELECT user_id FROM conversation_participants WHERE conversation_id=$1`, [convId],
          )
          for (const p of participants.rows) {
            app.io.to(`user:${p.user_id}`).emit('new_message', payload)
          }
          reply.status(201)
          return payload
        }
        // ── End forward ────────────────────────────────────────────────────────

        if (!content?.trim()) return reply.status(400).send({ error: 'content required' })

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
          [convId, userId, msgType, content.trim(), replyToId],
        )

        const msg = rows[0]

        await app.pg.query(`UPDATE conversations SET updated_at=NOW() WHERE id=$1`, [convId])

        const userRow = await app.pg.query<{ username: string; avatar_url: string | null }>(
          `SELECT username, avatar_url FROM users WHERE id=$1`, [userId],
        )

        const payload = {
          id: msg.id,
          content: msg.content,
          type: msg.type,
          createdAt: msg.created_at,
          replyToId: msg.reply_to_id,
          sender: { id: userId, username: userRow.rows[0].username, avatarUrl: userRow.rows[0].avatar_url },
          conversationId: convId,
          attachment: null,
        }

        const participants = await app.pg.query<{ user_id: string }>(
          `SELECT user_id FROM conversation_participants WHERE conversation_id=$1`, [convId],
        )
        for (const p of participants.rows) {
          app.io.to(`user:${p.user_id}`).emit('new_message', payload)
          if (p.user_id !== userId) {
            pushToUser(p.user_id, userRow.rows[0].username, payload.content)
          }
        }

        reply.status(201)
        return payload
      }
    },
  )

  // ----------------------------------------------------------------
  // PATCH /api/conversations/:id/messages/:msgId  — edit message text
  // ----------------------------------------------------------------
  app.patch<{ Params: EditMessageParams; Body: EditMessageBody }>(
    '/:id/messages/:msgId',
    async (request, reply) => {
      const { userId } = request.user
      const { id: convId, msgId } = request.params
      const { content } = request.body

      if (!content?.trim()) return reply.status(400).send({ error: 'content required' })

      // Check message exists, belongs to user, and is text type
      const msgCheck = await app.pg.query<{
        user_id: string; type: string
      }>(
        `SELECT user_id, type FROM messages WHERE id=$1 AND conversation_id=$2 AND deleted_at IS NULL`,
        [msgId, convId],
      )
      if (!msgCheck.rows[0]) return reply.status(404).send({ error: 'Message not found' })
      if (msgCheck.rows[0].user_id !== userId) return reply.status(403).send({ error: 'Forbidden' })
      if (msgCheck.rows[0].type !== 'text') return reply.status(400).send({ error: 'Only text messages can be edited' })

      const { rows } = await app.pg.query<{ id: string; content: string; edited_at: string }>(
        `UPDATE messages SET content=$1, edited_at=NOW() WHERE id=$2 RETURNING id, content, edited_at`,
        [content.trim(), msgId],
      )
      const updated = rows[0]

      const payload = {
        id: updated.id,
        content: updated.content,
        editedAt: updated.edited_at,
        conversationId: convId,
      }

      const participants = await app.pg.query<{ user_id: string }>(
        `SELECT user_id FROM conversation_participants WHERE conversation_id=$1`, [convId],
      )
      for (const p of participants.rows) {
        app.io.to(`user:${p.user_id}`).emit('message_edited', payload)
      }

      return payload
    },
  )

  // ----------------------------------------------------------------
  // DELETE /api/conversations/:id/messages/:msgId  — soft-delete
  // ----------------------------------------------------------------
  app.delete<{ Params: EditMessageParams }>(
    '/:id/messages/:msgId',
    async (request, reply) => {
      const { userId } = request.user
      const { id: convId, msgId } = request.params

      const msgCheck = await app.pg.query<{ user_id: string }>(
        `SELECT user_id FROM messages WHERE id=$1 AND conversation_id=$2 AND deleted_at IS NULL`,
        [msgId, convId],
      )
      if (!msgCheck.rows[0]) return reply.status(404).send({ error: 'Message not found' })
      if (msgCheck.rows[0].user_id !== userId) return reply.status(403).send({ error: 'Forbidden' })

      await app.pg.query(`UPDATE messages SET deleted_at=NOW() WHERE id=$1`, [msgId])

      const payload = { id: msgId, conversationId: convId }

      const participants = await app.pg.query<{ user_id: string }>(
        `SELECT user_id FROM conversation_participants WHERE conversation_id=$1`, [convId],
      )
      for (const p of participants.rows) {
        app.io.to(`user:${p.user_id}`).emit('message_deleted', payload)
      }

      reply.status(204)
    },
  )
}
