import Fastify from 'fastify'
import multipart from '@fastify/multipart'
import { dbPlugin } from './plugins/db.js'
import { redisPlugin } from './plugins/redis.js'
import { corsPlugin } from './plugins/cors.js'
import { jwtPlugin } from './plugins/jwt.js'
import { socketPlugin } from './plugins/socket.js'
import { s3Plugin } from './plugins/s3.js'
import { authRoutes } from './routes/auth/index.js'
import { conversationRoutes } from './routes/conversations/index.js'
import { userRoutes } from './routes/users/index.js'
import { healthRoutes } from './routes/health.js'

export async function buildApp() {
  const isProd = process.env.NODE_ENV === 'production'

  const app = Fastify({
    logger: {
      level: isProd ? 'info' : 'debug',
      ...(isProd
        ? {}
        : {
            transport: {
              target: 'pino-pretty',
              options: { colorize: true, translateTime: 'SYS:standard' },
            },
          }),
    },
  })

  await app.register(corsPlugin)
  await app.register(dbPlugin)
  await app.register(redisPlugin)
  await app.register(jwtPlugin)
  await app.register(socketPlugin)
  await app.register(s3Plugin)
  await app.register(multipart, {
    limits: { fileSize: 50 * 1024 * 1024 }, // 50 MB
  })

  await app.register(healthRoutes)
  await app.register(authRoutes, { prefix: '/api/auth' })
  await app.register(conversationRoutes, { prefix: '/api/conversations' })
  await app.register(userRoutes, { prefix: '/api/users' })

  return app
}
