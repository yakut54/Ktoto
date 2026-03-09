import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import { buildTestApp, truncateAll } from './helpers/app.js'
import { registerUser, createDirectConv, bearer } from './helpers/fixtures.js'
import type { FastifyInstance } from 'fastify'
import type { TestUser } from './helpers/fixtures.js'

describe('Users', () => {
  let app: FastifyInstance
  let alice: TestUser
  let bob: TestUser

  beforeAll(async () => { app = await buildTestApp() })
  afterAll(async () => { await app.close() })

  beforeEach(async () => {
    await truncateAll(app)
    alice = await registerUser(app, { username: 'alice', email: 'alice@t.com' })
    bob = await registerUser(app, { username: 'bob', email: 'bob@t.com' })
  })

  // ─── Search users ─────────────────────────────────────────────────────────

  describe('GET /api/users', () => {
    it('returns other users, not self', async () => {
      const res = await app.inject({
        method: 'GET', url: '/api/users?search=',
        headers: bearer(alice),
      })
      const list = res.json()
      expect(list.some((u: { id: string }) => u.id === alice.id)).toBe(false)
      expect(list.some((u: { id: string }) => u.id === bob.id)).toBe(true)
    })

    it('search filters by username', async () => {
      const res = await app.inject({
        method: 'GET', url: '/api/users?search=bob',
        headers: bearer(alice),
      })
      const list = res.json()
      expect(list).toHaveLength(1)
      expect(list[0].username).toBe('bob')
    })

    it('⚠ blocked user STILL appears in search (bug: blocks not applied to user search)', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      // Alice blocks Bob
      await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/block`,
        headers: bearer(alice),
      })

      const res = await app.inject({
        method: 'GET', url: '/api/users?search=bob',
        headers: bearer(alice),
      })
      // BUG: Bob still appears in search results after being blocked
      // Conversations list filters correctly, but user search does not
      expect(res.json().length).toBeGreaterThan(0) // documents the inconsistency
    })

    it('no auth → 401', async () => {
      const res = await app.inject({ method: 'GET', url: '/api/users' })
      expect(res.statusCode).toBe(401)
    })
  })

  // ─── Change password ──────────────────────────────────────────────────────

  describe('POST /api/users/change-password', () => {
    it('correct current password → ok', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'password123', newPassword: 'newpassword456' },
      })
      expect(res.statusCode).toBe(200)
      expect(res.json().ok).toBe(true)
    })

    it('wrong current password → 401', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'wrongpassword', newPassword: 'newpassword456' },
      })
      expect(res.statusCode).toBe(401)
      expect(res.json().error).toContain('Wrong current password')
    })

    it('new password too short → 400', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'password123', newPassword: 'short' },
      })
      expect(res.statusCode).toBe(400)
    })

    it('⚠ access token still works after password change (15min window)', async () => {
      // Change alice's password
      await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'password123', newPassword: 'newpassword456' },
      })

      // Old access token still works — SECURITY GAP (token not invalidated)
      const meRes = await app.inject({
        method: 'GET', url: '/api/auth/me',
        headers: bearer(alice),
      })
      // This PASSES — documents the security gap:
      // password change should invalidate existing sessions but does not
      expect(meRes.statusCode).toBe(200)
    })

    it('after password change can login with new password', async () => {
      await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'password123', newPassword: 'newpassword456' },
      })

      const loginRes = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: alice.username, password: 'newpassword456' },
      })
      expect(loginRes.statusCode).toBe(200)
    })

    it('after password change old password rejected', async () => {
      await app.inject({
        method: 'POST', url: '/api/users/change-password',
        headers: bearer(alice),
        payload: { currentPassword: 'password123', newPassword: 'newpassword456' },
      })

      const loginRes = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: alice.username, password: 'password123' },
      })
      expect(loginRes.statusCode).toBe(401)
    })
  })

  // ─── Blocked users ────────────────────────────────────────────────────────

  describe('GET /api/users/blocked', () => {
    it('empty blocked list for new user', async () => {
      const res = await app.inject({
        method: 'GET', url: '/api/users/blocked',
        headers: bearer(alice),
      })
      expect(res.statusCode).toBe(200)
      expect(res.json()).toEqual([])
    })

    it('after blocking user appears in blocked list', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/block`,
        headers: bearer(alice),
      })

      const res = await app.inject({
        method: 'GET', url: '/api/users/blocked',
        headers: bearer(alice),
      })
      const list = res.json()
      expect(list).toHaveLength(1)
      expect(list[0].id).toBe(bob.id)
      expect(list[0].username).toBe('bob')
      expect(list[0].blockedAt).toBeDefined()
    })
  })

  describe('DELETE /api/users/blocked/:userId', () => {
    it('unblock user → removed from list', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      // Block
      await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/block`,
        headers: bearer(alice),
      })

      // Unblock
      const unblockRes = await app.inject({
        method: 'DELETE', url: `/api/users/blocked/${bob.id}`,
        headers: bearer(alice),
      })
      expect(unblockRes.statusCode).toBe(200)

      // List should be empty
      const listRes = await app.inject({
        method: 'GET', url: '/api/users/blocked',
        headers: bearer(alice),
      })
      expect(listRes.json()).toHaveLength(0)
    })

    it('unblocking non-blocked user → 200 (idempotent)', async () => {
      const res = await app.inject({
        method: 'DELETE', url: `/api/users/blocked/${bob.id}`,
        headers: bearer(alice),
      })
      expect(res.statusCode).toBe(200)
    })
  })

  // ─── Redis: revokeAllUserTokens KEYS command ──────────────────────────────

  describe('TokenService.revokeAllUserTokens (⚠ uses KEYS *)', () => {
    it('revokes all refresh tokens for a user', async () => {
      // Create 3 refresh tokens for alice (simulates 3 devices)
      const { TokenService } = await import('../services/token.service.js')
      const svc = new TokenService(app)

      const t1 = await svc.generateRefreshToken(alice.id)
      const t2 = await svc.generateRefreshToken(alice.id)
      const t3 = await svc.generateRefreshToken(alice.id)

      // Revoke all
      await svc.revokeAllUserTokens(alice.id)

      // All tokens should now be invalid
      for (const token of [t1, t2, t3]) {
        await expect(svc.validateRefreshToken(token)).rejects.toThrow()
      }
    })

    it('⚠ KEYS * scans ALL Redis keys — would block Redis in production', async () => {
      // This test documents the bug: revokeAllUserTokens uses `redis.keys('refresh:*')`
      // which is O(N) and blocks Redis event loop.
      // Fix: use SCAN with cursor instead of KEYS.
      // For now we just document it.
      const { TokenService } = await import('../services/token.service.js')
      const svc = new TokenService(app)

      // Create tokens for two different users to confirm isolation
      await svc.generateRefreshToken(alice.id)
      await svc.generateRefreshToken(bob.id)

      const bobToken = await svc.generateRefreshToken(bob.id)

      // Revoking alice's tokens should NOT affect bob's
      await svc.revokeAllUserTokens(alice.id)
      const bobUserId = await svc.validateRefreshToken(bobToken)
      expect(bobUserId).toBe(bob.id) // bob's token still valid ✓
    })
  })
})
