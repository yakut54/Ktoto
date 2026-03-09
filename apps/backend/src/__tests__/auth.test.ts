import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import { buildTestApp, truncateAll } from './helpers/app.js'
import { registerUser } from './helpers/fixtures.js'
import type { FastifyInstance } from 'fastify'

describe('Auth', () => {
  let app: FastifyInstance

  beforeAll(async () => { app = await buildTestApp() })
  afterAll(async () => { await app.close() })
  beforeEach(async () => { await truncateAll(app) })

  // ─── Register ────────────────────────────────────────────────────────────

  describe('POST /api/auth/register', () => {
    it('happy path → 201 + tokens + user', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'alice@test.com', password: 'password123' },
      })
      expect(res.statusCode).toBe(201)
      const body = res.json()
      expect(body.accessToken).toBeDefined()
      expect(body.refreshToken).toBeDefined()
      expect(body.user.username).toBe('alice')
      expect(body.user.password).toBeUndefined() // password not leaked
      expect(body.user.password_hash).toBeUndefined()
    })

    it('duplicate username → 409', async () => {
      await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'alice@test.com', password: 'password123' },
      })
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'other@test.com', password: 'password123' },
      })
      expect(res.statusCode).toBe(409)
    })

    it('duplicate email → 409', async () => {
      await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'alice@test.com', password: 'password123' },
      })
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'bob', email: 'alice@test.com', password: 'password123' },
      })
      expect(res.statusCode).toBe(409)
    })

    it('invalid email → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'not-an-email', password: 'password123' },
      })
      expect(res.statusCode).toBe(400)
    })

    it('password too short (< 8) → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'alice@test.com', password: 'short' },
      })
      expect(res.statusCode).toBe(400)
    })

    it('username too short (< 3) → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'ab', email: 'alice@test.com', password: 'password123' },
      })
      expect(res.statusCode).toBe(400)
    })

    it('username with special chars → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice!@#', email: 'alice@test.com', password: 'password123' },
      })
      expect(res.statusCode).toBe(400)
    })

    it('missing fields → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice' },
      })
      expect(res.statusCode).toBe(400)
    })
  })

  // ─── Login ────────────────────────────────────────────────────────────────

  describe('POST /api/auth/login', () => {
    beforeEach(async () => {
      await app.inject({
        method: 'POST', url: '/api/auth/register',
        payload: { username: 'alice', email: 'alice@test.com', password: 'password123' },
      })
    })

    it('happy path → 200 + tokens', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: 'alice', password: 'password123' },
      })
      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.accessToken).toBeDefined()
      expect(body.refreshToken).toBeDefined()
    })

    it('wrong password → 401', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: 'alice', password: 'wrongpassword' },
      })
      expect(res.statusCode).toBe(401)
    })

    it('non-existent user → 401', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: 'nobody', password: 'password123' },
      })
      expect(res.statusCode).toBe(401)
    })

    it('banned user → 403 (⚠ reveals ban status to attacker)', async () => {
      const { rows } = await app.pg.query<{ id: string }>(
        `SELECT id FROM users WHERE username='alice'`,
      )
      await app.pg.query(`UPDATE users SET banned_at=NOW() WHERE id=$1`, [rows[0].id])

      const res = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: 'alice', password: 'password123' },
      })
      expect(res.statusCode).toBe(403)
      // NOTE: "Account is banned" error message leaks ban status — security concern
      expect(res.json().error).toContain('banned')
    })

    it('missing username → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { password: 'password123' },
      })
      expect(res.statusCode).toBe(400)
    })
  })

  // ─── Token refresh ────────────────────────────────────────────────────────

  describe('POST /api/auth/refresh', () => {
    it('valid refresh token → new token pair', async () => {
      const user = await registerUser(app)
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: user.refreshToken },
      })
      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.accessToken).toBeDefined()
      expect(body.refreshToken).toBeDefined()
      expect(body.refreshToken).not.toBe(user.refreshToken) // token rotated
    })

    it('token rotation: reusing old refresh token → 401 (replay attack blocked)', async () => {
      const user = await registerUser(app)
      // Use the refresh token once (rotates it)
      await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: user.refreshToken },
      })
      // Try to reuse the same old token
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: user.refreshToken },
      })
      expect(res.statusCode).toBe(401)
    })

    it('access token used as refresh token → 401', async () => {
      const user = await registerUser(app)
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: user.accessToken },
      })
      expect(res.statusCode).toBe(401)
    })

    it('random string → 401', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: 'not-a-jwt' },
      })
      expect(res.statusCode).toBe(401)
    })

    it('missing refreshToken → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: {},
      })
      expect(res.statusCode).toBe(400)
    })
  })

  // ─── Logout ───────────────────────────────────────────────────────────────

  describe('POST /api/auth/logout', () => {
    it('logout → 204 → refresh token revoked', async () => {
      const user = await registerUser(app)

      const logoutRes = await app.inject({
        method: 'POST', url: '/api/auth/logout',
        headers: { Authorization: `Bearer ${user.accessToken}` },
        payload: { refreshToken: user.refreshToken },
      })
      expect(logoutRes.statusCode).toBe(204)

      // Now refresh should fail
      const refreshRes = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: user.refreshToken },
      })
      expect(refreshRes.statusCode).toBe(401)
    })
  })

  // ─── GET /me ─────────────────────────────────────────────────────────────

  describe('GET /api/auth/me', () => {
    it('returns user with camelCase fields + no password', async () => {
      const user = await registerUser(app)
      const res = await app.inject({
        method: 'GET', url: '/api/auth/me',
        headers: { Authorization: `Bearer ${user.accessToken}` },
      })
      expect(res.statusCode).toBe(200)
      const body = res.json()
      expect(body.user.id).toBe(user.id)
      expect(body.user.username).toBe(user.username)
      expect(body.user.email).toBe(user.email)
      expect(body.user.avatarUrl).toBeNull() // no avatar yet — camelCase ✓
      expect(body.user.avatar_url).toBeUndefined() // snake_case should NOT leak
      expect(body.user.password_hash).toBeUndefined()
    })

    it('no auth → 401', async () => {
      const res = await app.inject({ method: 'GET', url: '/api/auth/me' })
      expect(res.statusCode).toBe(401)
    })

    it('refresh token used as access token → 401', async () => {
      const user = await registerUser(app)
      const res = await app.inject({
        method: 'GET', url: '/api/auth/me',
        headers: { Authorization: `Bearer ${user.refreshToken}` },
      })
      expect(res.statusCode).toBe(401)
    })
  })
})
