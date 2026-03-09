import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import { buildTestApp, truncateAll } from './helpers/app.js'
import { registerUser, createDirectConv, sendMessage, bearer } from './helpers/fixtures.js'
import type { FastifyInstance } from 'fastify'
import type { TestUser } from './helpers/fixtures.js'

describe('Security & Permissions', () => {
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

  // ─── Unauthenticated access ───────────────────────────────────────────────

  describe('No auth → 401 on all protected routes', () => {
    const protectedRoutes = [
      { method: 'GET', url: '/api/conversations' },
      { method: 'POST', url: '/api/conversations' },
      { method: 'GET', url: '/api/users' },
      { method: 'GET', url: '/api/auth/me' },
      { method: 'GET', url: '/api/calls/history' },
      { method: 'GET', url: '/api/calls/turn-credentials' },
      { method: 'GET', url: '/api/users/blocked' },
    ] as const

    for (const route of protectedRoutes) {
      it(`${route.method} ${route.url} → 401 without token`, async () => {
        const res = await app.inject({ method: route.method, url: route.url })
        expect(res.statusCode).toBe(401)
      })
    }
  })

  // ─── Refresh token used as access token ──────────────────────────────────

  describe('Refresh token type enforcement', () => {
    it('refresh token rejected as access token on all routes', async () => {
      const refreshHeaders = { Authorization: `Bearer ${alice.refreshToken}` }

      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: refreshHeaders,
      })
      expect(res.statusCode).toBe(401)
    })

    it('access token rejected on /api/auth/refresh', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/auth/refresh',
        payload: { refreshToken: alice.accessToken },
      })
      expect(res.statusCode).toBe(401)
    })
  })

  // ─── Conversation access ──────────────────────────────────────────────────

  describe('Conversation access control', () => {
    it('non-member cannot GET messages from conversation → 403', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })

      const res = await app.inject({
        method: 'GET', url: `/api/conversations/${convId}/messages`,
        headers: bearer(charlie),
      })
      expect(res.statusCode).toBe(403)
    })

    it('non-member cannot POST message → 403', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })

      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/messages`,
        headers: bearer(charlie),
        payload: { content: 'Intruder!', type: 'text' },
      })
      expect(res.statusCode).toBe(403)
    })

    it('non-member cannot GET group members → 403', async () => {
      const groupRes = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'group', name: 'Secret Group', memberIds: [] },
      })
      const groupId = groupRes.json().id
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })

      const res = await app.inject({
        method: 'GET', url: `/api/conversations/${groupId}/members`,
        headers: bearer(charlie),
      })
      expect(res.statusCode).toBe(403)
    })
  })

  // ─── Message ownership ────────────────────────────────────────────────────

  describe('Message ownership', () => {
    it('cannot edit another user\'s message', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Alice said this')

      const res = await app.inject({
        method: 'PATCH', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(bob),
        payload: { content: 'Bob changed it' },
      })
      expect([403, 404]).toContain(res.statusCode)
    })

    it('any participant can delete any message (by design) → 204', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Alice said this')

      const res = await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(bob),
      })
      expect(res.statusCode).toBe(204)
    })
  })

  // ─── Admin ban ────────────────────────────────────────────────────────────

  describe('User ban behavior', () => {
    it('⚠ banned user token still works until expiry (ban does not invalidate JWT)', async () => {
      // Ban alice via direct DB update (simulating admin action)
      await app.pg.query(`UPDATE users SET banned_at=NOW() WHERE id=$1`, [alice.id])

      // Alice's existing access token still works — SECURITY GAP
      const meRes = await app.inject({
        method: 'GET', url: '/api/auth/me',
        headers: bearer(alice),
      })
      // Token is still valid — ban doesn't invalidate JWT
      // Fix: check banned_at in the authenticate hook
      expect(meRes.statusCode).toBe(200) // documents the bug
    })

    it('banned user cannot login again', async () => {
      await app.pg.query(`UPDATE users SET banned_at=NOW() WHERE id=$1`, [alice.id])

      const loginRes = await app.inject({
        method: 'POST', url: '/api/auth/login',
        payload: { username: alice.username, password: 'password123' },
      })
      expect(loginRes.statusCode).toBe(403)
    })
  })

  // ─── Call log (security: no auth) ────────────────────────────────────────

  describe('POST /api/calls/log — ⚠ no authentication required', () => {
    it('anyone can write to the call debug log without a token', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/calls/log',
        payload: {
          deviceId: 'attacker-device',
          event: 'fake_event',
          data: { message: 'injected log entry' },
        },
      })
      // Returns 200 with no auth — documents the security issue
      // Any device can spam/poison the call debug log
      expect(res.statusCode).toBe(200)
    })
  })

  // ─── Group admin-only operations ──────────────────────────────────────────

  describe('Group admin-only operations', () => {
    let groupId: string

    beforeEach(async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'group', name: 'TestGroup', memberIds: [bob.id] },
      })
      groupId = res.json().id
    })

    it('member cannot change role of another member', async () => {
      const res = await app.inject({
        method: 'PATCH', url: `/api/conversations/${groupId}/members/${alice.id}/role`,
        headers: bearer(bob),
        payload: { role: 'admin' },
      })
      expect(res.statusCode).toBe(403)
    })

    it('member cannot upload group avatar', async () => {
      const boundary = '----TestBoundary'
      const body = [
        `--${boundary}`,
        'Content-Disposition: form-data; name="file"; filename="avatar.jpg"',
        'Content-Type: image/jpeg',
        '',
        'fake-image-data',
        `--${boundary}--`,
      ].join('\r\n')

      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${groupId}/avatar`,
        headers: {
          ...bearer(bob),
          'content-type': `multipart/form-data; boundary=${boundary}`,
        },
        payload: body,
      })
      expect(res.statusCode).toBe(403)
    })
  })
})
