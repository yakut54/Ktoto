import type { FastifyInstance } from 'fastify'
import sharp from 'sharp'
import { Readable } from 'stream'
import * as argon2 from 'argon2'

async function streamToBuffer(stream: Readable): Promise<Buffer> {
  const chunks: Buffer[] = []
  for await (const chunk of stream) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk))
  }
  return Buffer.concat(chunks)
}

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

  // PATCH /api/users/profile — update username and/or avatar
  app.patch('/profile', async (request, reply) => {
    const { userId } = request.user
    const contentType = request.headers['content-type'] ?? ''

    if (contentType.includes('multipart/form-data')) {
      const parts = request.parts()
      let fileBuffer: Buffer | null = null
      let newUsername: string | null = null

      for await (const part of parts) {
        if (part.type === 'file') {
          fileBuffer = await streamToBuffer(part.file)
        } else if (part.type === 'field' && part.fieldname === 'username') {
          newUsername = (part.value as string).trim() || null
        }
      }

      let avatarUrl: string | null = null
      if (fileBuffer) {
        const resized = await sharp(fileBuffer)
          .resize(256, 256, { fit: 'cover' })
          .jpeg({ quality: 85 })
          .toBuffer()
        const s3Key = `avatars/users/${userId}.jpg`
        await app.s3.upload(s3Key, resized, 'image/jpeg')
        avatarUrl = await app.s3.presignedUrl(s3Key)
        await app.pg.query('UPDATE users SET avatar_url=$1 WHERE id=$2', [s3Key, userId])
      }

      if (newUsername) {
        // Check uniqueness
        const { rows } = await app.pg.query(
          'SELECT id FROM users WHERE username=$1 AND id!=$2',
          [newUsername, userId],
        )
        if (rows.length > 0) return reply.status(409).send({ error: 'Username already taken' })
        await app.pg.query('UPDATE users SET username=$1 WHERE id=$2', [newUsername, userId])
      }

      const { rows } = await app.pg.query<{
        id: string; username: string; email: string; avatar_url: string | null
      }>('SELECT id, username, email, avatar_url FROM users WHERE id=$1', [userId])
      const u = rows[0]
      const finalAvatarUrl = avatarUrl ?? (u.avatar_url ? await app.s3.presignedUrl(u.avatar_url) : null)
      return { user: { id: u.id, username: u.username, email: u.email, avatarUrl: finalAvatarUrl } }
    }

    return reply.status(400).send({ error: 'Multipart required' })
  })

  // POST /api/users/change-password
  app.post<{ Body: { currentPassword: string; newPassword: string } }>(
    '/change-password',
    async (request, reply) => {
      const { userId } = request.user
      const { currentPassword, newPassword } = request.body

      if (!currentPassword || !newPassword || newPassword.length < 8) {
        return reply.status(400).send({ error: 'Invalid input' })
      }

      const { rows } = await app.pg.query<{ password_hash: string }>(
        'SELECT password_hash FROM users WHERE id=$1',
        [userId],
      )
      if (!rows[0]) return reply.status(404).send({ error: 'User not found' })

      const valid = await argon2.verify(rows[0].password_hash, currentPassword)
      if (!valid) return reply.status(401).send({ error: 'Wrong current password' })

      const newHash = await argon2.hash(newPassword)
      await app.pg.query('UPDATE users SET password_hash=$1 WHERE id=$2', [newHash, userId])
      return { ok: true }
    },
  )

  // GET /api/users/blocked — list blocked users
  app.get('/blocked', async (request) => {
    const { userId } = request.user
    const { rows } = await app.pg.query<{
      id: string; username: string; avatar_url: string | null; blocked_at: string
    }>(
      `SELECT u.id, u.username, u.avatar_url, b.created_at AS blocked_at
       FROM blocked_users b
       JOIN users u ON u.id = b.blocked_id
       WHERE b.blocker_id=$1
       ORDER BY b.created_at DESC`,
      [userId],
    )
    return rows.map((r) => ({
      id: r.id,
      username: r.username,
      avatarUrl: r.avatar_url,
      blockedAt: r.blocked_at,
    }))
  })

  // DELETE /api/users/blocked/:userId — unblock a user
  app.delete<{ Params: { userId: string } }>('/blocked/:userId', async (request, reply) => {
    const { userId } = request.user
    const { userId: targetId } = request.params
    await app.pg.query(
      'DELETE FROM blocked_users WHERE blocker_id=$1 AND blocked_id=$2',
      [userId, targetId],
    )
    return reply.send({ ok: true })
  })
}
