import Fastify from 'fastify'
import multipart from '@fastify/multipart'
import fp from 'fastify-plugin'
import { vi } from 'vitest'
import { Server } from 'socket.io'
import { dbPlugin } from '../../plugins/db.js'
import { redisPlugin } from '../../plugins/redis.js'
import { jwtPlugin } from '../../plugins/jwt.js'
import { authorizePlugin } from '../../plugins/authorize.js'
import { authRoutes } from '../../routes/auth/index.js'
import { conversationRoutes } from '../../routes/conversations/index.js'
import { userRoutes } from '../../routes/users/index.js'
import { callRoutes } from '../../routes/calls/index.js'
import type { FastifyInstance } from 'fastify'

/** Mock S3 — no MinIO needed in tests */
const mockS3Plugin = fp(async (app: FastifyInstance) => {
  app.decorate('s3', {
    upload: vi.fn().mockResolvedValue(undefined),
    presignedUrl: vi.fn().mockImplementation((key: string) =>
      Promise.resolve(`http://test-s3:9000/ktoto-media/${key}?mock=1`),
    ),
  })
})

/**
 * Mock Socket.IO plugin — avoids the real socketPlugin which:
 * 1. Starts a call watchdog setInterval that keeps the process alive
 * 2. Marks users online/offline on DB changes (unnecessary in unit tests)
 * Routes still call app.io.to(...).emit(...) but those are no-ops here.
 */
const mockSocketPlugin = fp(async (app: FastifyInstance) => {
  const io = new Server() // no HTTP server attached → no connections possible
  app.decorate('io', io)
  app.addHook('onClose', async () => { try { io.close() } catch { /* no http server attached */ } })
})

export async function buildTestApp(): Promise<FastifyInstance> {
  const app = Fastify({ logger: false })

  await app.register(dbPlugin)
  await app.register(redisPlugin)
  await app.register(jwtPlugin)
  await app.register(authorizePlugin)
  await app.register(mockSocketPlugin) // real socketPlugin would start call watchdog setInterval
  await app.register(mockS3Plugin)
  await app.register(multipart, { limits: { fileSize: 50 * 1024 * 1024 } })

  await app.register(authRoutes, { prefix: '/api/auth' })
  await app.register(conversationRoutes, { prefix: '/api/conversations' })
  await app.register(userRoutes, { prefix: '/api/users' })
  await app.register(callRoutes, { prefix: '/api/calls' })

  await app.ready()
  return app
}

/** Truncate all data tables between tests */
export async function truncateAll(app: FastifyInstance) {
  await app.pg.query(`
    TRUNCATE TABLE
      file_attachments, messages, conversation_participants,
      conversations, blocked_users, calls, users
    RESTART IDENTITY CASCADE
  `)
  await app.redis.flushdb()
}
