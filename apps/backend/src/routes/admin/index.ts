import type { FastifyInstance } from 'fastify'

export async function adminRoutes(app: FastifyInstance) {
  app.addHook('onRequest', app.authorizeAdmin)

  // ─── GET /api/admin/stats ─────────────────────────────────────────────────
  app.get('/stats', async () => {
    const { rows: [stats] } = await app.pg.query(`
      SELECT
        (SELECT COUNT(*) FROM users)                                              AS total_users,
        (SELECT COUNT(*) FROM conversations)                                     AS total_conversations,
        (SELECT COUNT(*) FROM messages WHERE created_at >= NOW() - INTERVAL '24h' AND deleted_at IS NULL) AS messages_today,
        (SELECT COUNT(DISTINCT user_id) FROM messages WHERE created_at >= NOW() - INTERVAL '24h') AS active_users_24h
    `)

    const { rows: msgChart } = await app.pg.query(`
      SELECT
        to_char(d::date, 'YYYY-MM-DD') AS date,
        COUNT(m.id)::int               AS count
      FROM generate_series(
        NOW() - INTERVAL '29 days',
        NOW(),
        '1 day'::interval
      ) AS d
      LEFT JOIN messages m
        ON m.created_at::date = d::date AND m.deleted_at IS NULL
      GROUP BY d
      ORDER BY d
    `)

    const { rows: userChart } = await app.pg.query(`
      SELECT
        to_char(date_trunc('week', d), 'YYYY-MM-DD') AS week,
        COUNT(u.id)::int                             AS count
      FROM generate_series(
        NOW() - INTERVAL '11 weeks',
        NOW(),
        '1 week'::interval
      ) AS d
      LEFT JOIN users u
        ON date_trunc('week', u.created_at) = date_trunc('week', d)
      GROUP BY date_trunc('week', d)
      ORDER BY date_trunc('week', d)
    `)

    return {
      totalUsers: parseInt(stats.total_users),
      totalConversations: parseInt(stats.total_conversations),
      messagesToday: parseInt(stats.messages_today),
      activeUsers24h: parseInt(stats.active_users_24h),
      messagesChart: msgChart,
      usersChart: userChart,
    }
  })

  // ─── GET /api/admin/users ─────────────────────────────────────────────────
  app.get<{
    Querystring: { page?: string; limit?: string; search?: string }
  }>('/users', async (request) => {
    const page  = Math.max(1, parseInt(request.query.page  ?? '1'))
    const limit = Math.min(100, Math.max(1, parseInt(request.query.limit ?? '20')))
    const search = request.query.search?.trim() ?? ''
    const offset = (page - 1) * limit

    const { rows: users } = await app.pg.query(
      `SELECT id, username, email, avatar_url, role, banned_at, status, last_seen_at, created_at
       FROM users
       WHERE ($1 = '' OR username ILIKE $2 OR email ILIKE $2)
       ORDER BY created_at DESC
       LIMIT $3 OFFSET $4`,
      [search, `%${search}%`, limit, offset]
    )

    const { rows: [{ count }] } = await app.pg.query(
      `SELECT COUNT(*)::int AS count FROM users WHERE ($1 = '' OR username ILIKE $2 OR email ILIKE $2)`,
      [search, `%${search}%`]
    )

    return { users, total: count, page, limit }
  })

  // ─── GET /api/admin/users/:id ─────────────────────────────────────────────
  app.get<{ Params: { id: string } }>('/users/:id', async (request, reply) => {
    const { id } = request.params

    const { rows: [user] } = await app.pg.query(
      `SELECT id, username, email, avatar_url, role, banned_at, status, last_seen_at, created_at
       FROM users WHERE id = $1`,
      [id]
    )

    if (!user) return reply.status(404).send({ error: 'User not found' })

    const { rows: [msgStats] } = await app.pg.query(
      `SELECT
        COUNT(*)::int                                    AS message_count,
        COUNT(DISTINCT conversation_id)::int             AS conversation_count
       FROM messages WHERE user_id = $1 AND deleted_at IS NULL`,
      [id]
    )

    return { ...user, stats: msgStats }
  })

  // ─── PATCH /api/admin/users/:id ───────────────────────────────────────────
  app.patch<{
    Params: { id: string }
    Body: { action: 'ban' | 'unban' | 'set_role'; role?: string }
  }>('/users/:id', async (request, reply) => {
    const { id } = request.params
    const { action, role } = request.body

    if (action === 'ban') {
      await app.pg.query('UPDATE users SET banned_at = NOW() WHERE id = $1', [id])
      return { success: true, action: 'ban' }
    }

    if (action === 'unban') {
      await app.pg.query('UPDATE users SET banned_at = NULL WHERE id = $1', [id])
      return { success: true, action: 'unban' }
    }

    if (action === 'set_role') {
      if (!role || !['user', 'admin'].includes(role)) {
        return reply.status(400).send({ error: 'Invalid role' })
      }
      await app.pg.query('UPDATE users SET role = $1 WHERE id = $2', [role, id])
      return { success: true, action: 'set_role', role }
    }

    return reply.status(400).send({ error: 'Invalid action' })
  })

  // ─── GET /api/admin/conversations ─────────────────────────────────────────
  app.get<{
    Querystring: { page?: string; limit?: string }
  }>('/conversations', async (request) => {
    const page  = Math.max(1, parseInt(request.query.page  ?? '1'))
    const limit = Math.min(100, Math.max(1, parseInt(request.query.limit ?? '20')))
    const offset = (page - 1) * limit

    const { rows: conversations } = await app.pg.query(
      `SELECT
         c.id, c.name, c.type, c.avatar_url, c.created_at, c.updated_at,
         COUNT(DISTINCT cp.user_id)::int  AS participant_count,
         COUNT(DISTINCT m.id)::int        AS message_count,
         MAX(m.created_at)                AS last_message_at
       FROM conversations c
       LEFT JOIN conversation_participants cp ON cp.conversation_id = c.id
       LEFT JOIN messages m ON m.conversation_id = c.id AND m.deleted_at IS NULL
       GROUP BY c.id
       ORDER BY last_message_at DESC NULLS LAST
       LIMIT $1 OFFSET $2`,
      [limit, offset]
    )

    const { rows: [{ count }] } = await app.pg.query(
      'SELECT COUNT(*)::int AS count FROM conversations'
    )

    return { conversations, total: count, page, limit }
  })

  // ─── GET /api/admin/conversations/:id/messages ────────────────────────────
  app.get<{
    Params: { id: string }
    Querystring: { page?: string; limit?: string }
  }>('/conversations/:id/messages', async (request) => {
    const { id } = request.params
    const page  = Math.max(1, parseInt(request.query.page  ?? '1'))
    const limit = Math.min(100, Math.max(1, parseInt(request.query.limit ?? '50')))
    const offset = (page - 1) * limit

    const { rows: messages } = await app.pg.query(
      `SELECT
         m.id, m.type, m.content, m.created_at, m.edited_at, m.deleted_at,
         u.id AS user_id, u.username, u.avatar_url
       FROM messages m
       LEFT JOIN users u ON u.id = m.user_id
       WHERE m.conversation_id = $1
       ORDER BY m.created_at DESC
       LIMIT $2 OFFSET $3`,
      [id, limit, offset]
    )

    const { rows: [{ count }] } = await app.pg.query(
      'SELECT COUNT(*)::int AS count FROM messages WHERE conversation_id = $1',
      [id]
    )

    return { messages, total: count, page, limit }
  })

  // ─── DELETE /api/admin/messages/:id ──────────────────────────────────────
  app.delete<{ Params: { id: string } }>('/messages/:id', async (request, reply) => {
    const { id } = request.params

    const { rowCount } = await app.pg.query(
      'DELETE FROM messages WHERE id = $1',
      [id]
    )

    if (!rowCount) return reply.status(404).send({ error: 'Message not found' })

    return { success: true }
  })
}
