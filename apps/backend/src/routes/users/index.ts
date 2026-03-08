import type { FastifyInstance } from 'fastify'

export async function userRoutes(app: FastifyInstance) {
  app.addHook('onRequest', app.authenticate)

  // PUT /api/users/fcm-token
  app.put<{ Body: { fcmToken: string } }>('/fcm-token', async (request, reply) => {
    const { fcmToken } = request.body
    await app.pg.query('UPDATE users SET fcm_token = $1 WHERE id = $2', [fcmToken, request.user.userId])
    reply.send({ ok: true })
  })

  // GET /api/users?search=username
  app.get<{ Querystring: { search?: string } }>('/', async (request) => {
    const { userId } = request.user
    const search = request.query.search?.trim() ?? ''

    const { rows } = await app.pg.query<{
      id: string
      username: string
      avatar_url: string | null
    }>(
      `SELECT id, username, avatar_url FROM users
       WHERE id != $1
         AND ($2 = '' OR username ILIKE $3)
       ORDER BY username
       LIMIT 20`,
      [userId, search, `%${search}%`],
    )

    return rows.map((r) => ({ id: r.id, username: r.username, avatarUrl: r.avatar_url }))
  })
}
