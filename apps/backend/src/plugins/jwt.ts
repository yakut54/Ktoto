import fp from 'fastify-plugin'
import fastifyJwt from '@fastify/jwt'
import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify'

declare module '@fastify/jwt' {
  interface FastifyJWT {
    payload: { userId: string; type: 'access' | 'refresh' }
    user: { userId: string; type: 'access' | 'refresh' }
  }
}

export const jwtPlugin = fp(async (app: FastifyInstance) => {
  await app.register(fastifyJwt, {
    secret: process.env.JWT_SECRET || 'dev_secret_change_in_production',
  })

  app.decorate('authenticate', async (request: FastifyRequest, reply: FastifyReply) => {
    try {
      await request.jwtVerify()
      if (request.user.type !== 'access') {
        reply.status(401).send({ error: 'Invalid token type' })
      }
    } catch {
      reply.status(401).send({ error: 'Unauthorized' })
    }
  })
})

declare module 'fastify' {
  interface FastifyInstance {
    authenticate: (request: FastifyRequest, reply: FastifyReply) => Promise<void>
  }
}
