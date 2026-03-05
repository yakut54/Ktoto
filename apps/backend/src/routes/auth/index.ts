import type { FastifyInstance } from 'fastify'
import { AuthService } from '../../services/auth.service.js'
import { TokenService } from '../../services/token.service.js'
import { z } from 'zod'

const registerSchema = z.object({
  username: z.string().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/),
  email: z.string().email(),
  password: z.string().min(8).max(100),
})

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
})

const refreshSchema = z.object({
  refreshToken: z.string().min(1),
})

export async function authRoutes(app: FastifyInstance) {
  const authService = new AuthService(app)
  const tokenService = new TokenService(app)

  // POST /api/auth/register
  app.post('/register', async (request, reply) => {
    const parsed = registerSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: parsed.error.flatten() })
    }

    try {
      const user = await authService.register(parsed.data)
      const accessToken = tokenService.generateAccessToken(user.id)
      const refreshToken = await tokenService.generateRefreshToken(user.id)

      return reply.status(201).send({ user, accessToken, refreshToken })
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Registration failed'
      return reply.status(409).send({ error: message })
    }
  })

  // POST /api/auth/login
  app.post('/login', async (request, reply) => {
    const parsed = loginSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: parsed.error.flatten() })
    }

    try {
      const user = await authService.login(parsed.data)
      const accessToken = tokenService.generateAccessToken(user.id)
      const refreshToken = await tokenService.generateRefreshToken(user.id)

      return { user, accessToken, refreshToken }
    } catch {
      return reply.status(401).send({ error: 'Invalid credentials' })
    }
  })

  // POST /api/auth/refresh
  app.post('/refresh', async (request, reply) => {
    const parsed = refreshSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: 'refreshToken required' })
    }

    try {
      const userId = await tokenService.validateRefreshToken(parsed.data.refreshToken)
      await tokenService.revokeRefreshToken(parsed.data.refreshToken) // Rotation

      const accessToken = tokenService.generateAccessToken(userId)
      const newRefreshToken = await tokenService.generateRefreshToken(userId)

      return { accessToken, refreshToken: newRefreshToken }
    } catch {
      return reply.status(401).send({ error: 'Invalid or expired refresh token' })
    }
  })

  // POST /api/auth/logout
  app.post('/logout', { preHandler: [app.authenticate] }, async (request, reply) => {
    const parsed = refreshSchema.safeParse(request.body)
    if (parsed.success) {
      await tokenService.revokeRefreshToken(parsed.data.refreshToken)
    }
    return reply.status(204).send()
  })

  // GET /api/auth/me
  app.get('/me', { preHandler: [app.authenticate] }, async (request, reply) => {
    const user = await authService.getById(request.user.userId)
    if (!user) {
      return reply.status(404).send({ error: 'User not found' })
    }
    return { user }
  })
}
