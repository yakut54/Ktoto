import fp from 'fastify-plugin'
import { Server } from 'socket.io'
import type { FastifyInstance } from 'fastify'

export const socketPlugin = fp(async (app: FastifyInstance) => {
  const io = new Server(app.server, {
    cors: {
      origin: process.env.CORS_ORIGIN?.split(',') || '*',
      credentials: true,
    },
  })

  // JWT auth middleware
  io.use((socket, next) => {
    const token = socket.handshake.auth.token as string | undefined
    if (!token) {
      return next(new Error('Authentication required'))
    }
    try {
      const payload = app.jwt.verify<{ userId: string; type: string }>(token)
      if (payload.type !== 'access') {
        return next(new Error('Invalid token type'))
      }
      socket.data.userId = payload.userId
      next()
    } catch {
      next(new Error('Invalid token'))
    }
  })

  io.on('connection', async (socket) => {
    const { userId } = socket.data as { userId: string }
    app.log.info({ userId }, 'Socket connected')

    // Personal room — for delivering messages to a specific user
    socket.join(`user:${userId}`)

    // Mark online in DB
    await app.pg.query(`UPDATE users SET status='online' WHERE id=$1`, [userId])

    // Fetch all conversation partners once — reused in disconnect handler
    const { rows: partners } = await app.pg.query<{ user_id: string }>(
      `SELECT DISTINCT cp2.user_id
       FROM conversation_participants cp1
       JOIN conversation_participants cp2 ON cp2.conversation_id = cp1.conversation_id AND cp2.user_id != $1
       WHERE cp1.user_id = $1`,
      [userId],
    )

    // Notify partners: this user is online
    for (const p of partners) {
      app.io.to(`user:${p.user_id}`).emit('user_status', { userId, status: 'online' })
    }

    // typing: { conversationId } → broadcast to other participants
    socket.on('typing', async ({ conversationId }: { conversationId: string }) => {
      const { rows } = await app.pg.query<{ user_id: string }>(
        `SELECT user_id FROM conversation_participants WHERE conversation_id=$1 AND user_id!=$2`,
        [conversationId, userId],
      )
      for (const p of rows) {
        app.io.to(`user:${p.user_id}`).emit('typing', { conversationId, userId })
      }
    })

    socket.on('disconnect', async (reason) => {
      app.log.info({ userId, reason }, 'Socket disconnected')

      // Mark offline + update last_seen_at
      const { rows } = await app.pg.query<{ last_seen_at: string }>(
        `UPDATE users SET status='offline', last_seen_at=NOW() WHERE id=$1 RETURNING last_seen_at`,
        [userId],
      )
      const lastSeenAt = rows[0]?.last_seen_at

      // Notify partners: this user is offline
      for (const p of partners) {
        app.io.to(`user:${p.user_id}`).emit('user_status', { userId, status: 'offline', lastSeenAt })
      }
    })
  })

  app.decorate('io', io)

  app.addHook('onClose', async () => {
    await io.close()
  })
})

declare module 'fastify' {
  interface FastifyInstance {
    io: Server
  }
}
