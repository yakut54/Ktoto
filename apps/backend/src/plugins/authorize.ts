import fp from 'fastify-plugin'
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify'

export const authorizePlugin = fp(async (app: FastifyInstance) => {
  app.decorate('authorizeAdmin', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      await request.jwtVerify()
    } catch {
      return reply.status(401).send({ error: 'Unauthorized' })
    }

    if (request.user.type !== 'access') {
      return reply.status(401).send({ error: 'Invalid token type' })
    }

    if (request.user.role !== 'admin') {
      return reply.status(403).send({ error: 'Access denied: admin only' })
    }
  })
})

declare module 'fastify' {
  interface FastifyInstance {
    authorizeAdmin: (request: FastifyRequest, reply: FastifyReply) => Promise<void>
  }
}
