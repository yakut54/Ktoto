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

  io.on('connection', (socket) => {
    const { userId } = socket.data as { userId: string }
    app.log.info({ userId }, 'Socket connected')

    // Personal room — for delivering messages to a specific user
    socket.join(`user:${userId}`)

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

    socket.on('disconnect', (reason) => {
      app.log.info({ userId, reason }, 'Socket disconnected')
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
