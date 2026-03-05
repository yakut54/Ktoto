import type { FastifyInstance } from 'fastify'

export async function healthRoutes(app: FastifyInstance) {
  app.get('/health', async () => {
    return { status: 'ok', timestamp: new Date().toISOString() }
  })

  app.get('/health/db', async (_, reply) => {
    try {
      const client = await app.pg.connect()
      await client.query('SELECT 1')
      client.release()
      return { status: 'ok', db: 'connected' }
    } catch (err) {
      reply.status(503)
      return { status: 'error', db: 'disconnected' }
    }
  })
}
