import { describe, it, expect, beforeAll, afterAll, beforeEach } from 'vitest'
import { buildTestApp, truncateAll } from './helpers/app.js'
import { registerUser, createDirectConv, sendMessage, bearer } from './helpers/fixtures.js'
import type { FastifyInstance } from 'fastify'
import type { TestUser } from './helpers/fixtures.js'

describe('Conversations', () => {
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

  // ─── Create conversation ──────────────────────────────────────────────────

  describe('POST /api/conversations', () => {
    it('create direct → 201 + both users as participants', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'direct', userId: bob.id },
      })
      expect(res.statusCode).toBe(201)
      const { id } = res.json()
      expect(id).toBeDefined()

      // Both participants should be in the DB
      const { rows } = await app.pg.query(
        `SELECT COUNT(*) FROM conversation_participants WHERE conversation_id=$1`,
        [id],
      )
      expect(Number(rows[0].count)).toBe(2)
    })

    it('create direct is idempotent → same conv_id on second call', async () => {
      const res1 = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice), payload: { type: 'direct', userId: bob.id },
      })
      const res2 = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice), payload: { type: 'direct', userId: bob.id },
      })
      expect(res1.json().id).toBe(res2.json().id)
    })

    it('create group → 201 + system message emitted', async () => {
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })
      const res = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'group', name: 'Test Group', memberIds: [bob.id, charlie.id] },
      })
      expect(res.statusCode).toBe(201)
      const { id } = res.json()

      // System message should exist
      const { rows } = await app.pg.query(
        `SELECT type, content FROM messages WHERE conversation_id=$1`,
        [id],
      )
      expect(rows.length).toBe(1)
      expect(rows[0].type).toBe('system')
      expect(rows[0].content).toContain('Test Group')
    })

    it('create group without name → 500 or 400 (⚠ no validation)', async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'group', memberIds: [bob.id] },
      })
      // Backend doesn't validate name presence — documents the missing validation
      expect([400, 500]).toContain(res.statusCode)
    })
  })

  // ─── List conversations ───────────────────────────────────────────────────

  describe('GET /api/conversations', () => {
    it('new user → empty list', async () => {
      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: bearer(alice),
      })
      expect(res.statusCode).toBe(200)
      expect(res.json()).toEqual([])
    })

    it('shows created conversation with correct fields', async () => {
      await createDirectConv(app, alice, bob.id)
      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: bearer(alice),
      })
      const list = res.json()
      expect(list).toHaveLength(1)
      expect(list[0].type).toBe('direct')
      expect(list[0].unreadCount).toBe(0)
    })

    it('blocked user conversation hidden from list', async () => {
      const convId = await createDirectConv(app, alice, bob.id)

      // Alice blocks Bob
      await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/block`,
        headers: bearer(alice),
      })

      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: bearer(alice),
      })
      expect(res.json()).toHaveLength(0)
    })

    it('unread count increments per message from other user', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      await sendMessage(app, bob, convId, 'msg 1')
      await sendMessage(app, bob, convId, 'msg 2')
      await sendMessage(app, bob, convId, 'msg 3')

      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: bearer(alice),
      })
      expect(res.json()[0].unreadCount).toBe(3)
    })

    it('reading messages resets unread count to 0', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      await sendMessage(app, bob, convId, 'msg 1')
      await sendMessage(app, bob, convId, 'msg 2')

      // Alice reads messages
      await app.inject({
        method: 'GET', url: `/api/conversations/${convId}/messages`,
        headers: bearer(alice),
      })

      const res = await app.inject({
        method: 'GET', url: '/api/conversations',
        headers: bearer(alice),
      })
      expect(res.json()[0].unreadCount).toBe(0)
    })
  })

  // ─── Messages ─────────────────────────────────────────────────────────────

  describe('POST /api/conversations/:id/messages', () => {
    it('send text message → message with sender', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/messages`,
        headers: bearer(alice),
        payload: { content: 'Hello Bob!', type: 'text' },
      })
      expect(res.statusCode).toBe(201)
      const msg = res.json()
      expect(msg.content).toBe('Hello Bob!')
      expect(msg.type).toBe('text')
      expect(msg.sender.id).toBe(alice.id)
      expect(msg.sender.username).toBe('alice')
    })

    it('non-member cannot send message → 403', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })
      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/messages`,
        headers: bearer(charlie),
        payload: { content: 'Hacking!', type: 'text' },
      })
      expect(res.statusCode).toBe(403)
    })

    it('reply to message → reply_to set on returned message', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const originalMsg = await sendMessage(app, alice, convId, 'Original')

      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/messages`,
        headers: bearer(bob),
        payload: { content: 'Reply!', type: 'text', reply_to_id: originalMsg.id },
      })
      expect(res.statusCode).toBe(201)
      expect(res.json().replyToId).toBe(originalMsg.id)
    })

    it('reply to deleted message → does not crash (reply preview null)', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const originalMsg = await sendMessage(app, alice, convId, 'Original')

      // Delete the original
      await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${originalMsg.id}`,
        headers: bearer(alice),
      })

      // Reply to deleted message — should succeed (references deleted msg)
      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${convId}/messages`,
        headers: bearer(bob),
        payload: { content: 'Reply to deleted', type: 'text', reply_to_id: originalMsg.id },
      })
      expect(res.statusCode).toBe(201)
      // replyTo preview should be null since message is soft-deleted
      expect(res.json().replyTo).toBeNull()
    })
  })

  // ─── Edit message ─────────────────────────────────────────────────────────

  describe('PATCH /api/conversations/:id/messages/:msgId', () => {
    it('edit own message → updated content', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Original')

      const res = await app.inject({
        method: 'PATCH', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(alice),
        payload: { content: 'Edited!' },
      })
      expect(res.statusCode).toBe(200)
      expect(res.json().content).toBe('Edited!')
      expect(res.json().editedAt).not.toBeNull()
    })

    it('edit another user\'s message → 404 or 403', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Alice message')

      const res = await app.inject({
        method: 'PATCH', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(bob),
        payload: { content: 'Bob hijacking!' },
      })
      expect([403, 404]).toContain(res.statusCode)
    })
  })

  // ─── Delete message ───────────────────────────────────────────────────────

  describe('DELETE /api/conversations/:id/messages/:msgId', () => {
    it('delete own message → soft delete (deleted_at set)', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Delete me')

      const res = await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(alice),
      })
      expect(res.statusCode).toBe(204)

      // deleted_at should be set in DB
      const { rows } = await app.pg.query(
        `SELECT deleted_at FROM messages WHERE id=$1`, [msg.id],
      )
      expect(rows[0].deleted_at).not.toBeNull()
    })

    it('deleted message not returned in GET messages', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Delete me')

      await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(alice),
      })

      const res = await app.inject({
        method: 'GET', url: `/api/conversations/${convId}/messages`,
        headers: bearer(alice),
      })
      const messages = res.json()
      expect(messages.find((m: { id: string }) => m.id === msg.id)).toBeUndefined()
    })

    it('⚠ delete does NOT remove S3 file (orphaned files accumulate)', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'text msg')

      // Add a fake file_attachment record to document the gap
      await app.pg.query(`
        INSERT INTO file_attachments (message_id, file_name, file_size_bytes, mime_type, file_type, s3_key)
        VALUES ($1, 'test.jpg', 1024, 'image/jpeg', 'image', 'messages/test.jpg')
      `, [msg.id])

      await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(alice),
      })

      // S3 file still in DB (and would still be in S3)
      const { rows } = await app.pg.query(
        `SELECT id FROM file_attachments WHERE message_id=$1`, [msg.id],
      )
      // This test DOCUMENTS the bug: file_attachments not cleaned up on message delete
      expect(rows.length).toBe(1) // orphaned record still exists
    })

    it('cannot delete another user\'s message', async () => {
      const convId = await createDirectConv(app, alice, bob.id)
      const msg = await sendMessage(app, alice, convId, 'Alice message')

      const res = await app.inject({
        method: 'DELETE', url: `/api/conversations/${convId}/messages/${msg.id}`,
        headers: bearer(bob),
      })
      expect([403, 404]).toContain(res.statusCode)
    })
  })

  // ─── Cursor pagination ────────────────────────────────────────────────────

  describe('GET /api/conversations/:id/messages — pagination', () => {
    it('returns 20 messages by default, older 5 with cursor', async () => {
      const convId = await createDirectConv(app, alice, bob.id)

      // Send 25 messages sequentially
      for (let i = 1; i <= 25; i++) {
        await sendMessage(app, alice, convId, `msg ${i}`)
      }

      // First page: latest 20
      const page1 = await app.inject({
        method: 'GET', url: `/api/conversations/${convId}/messages?limit=20`,
        headers: bearer(alice),
      })
      const msgs1 = page1.json()
      expect(msgs1).toHaveLength(20)

      // Cursor: oldest message id from first page (index 0, since result is asc order)
      const cursor = msgs1[0].id
      const page2 = await app.inject({
        method: 'GET', url: `/api/conversations/${convId}/messages?limit=20&before=${cursor}`,
        headers: bearer(alice),
      })
      const msgs2 = page2.json()
      expect(msgs2).toHaveLength(5) // 25 - 20 = 5 remaining

      // No overlap
      const ids1 = new Set(msgs1.map((m: { id: string }) => m.id))
      for (const m of msgs2) {
        expect(ids1.has(m.id)).toBe(false)
      }
    })
  })

  // ─── Group permissions ────────────────────────────────────────────────────

  describe('Group member management', () => {
    let groupId: string

    beforeEach(async () => {
      const res = await app.inject({
        method: 'POST', url: '/api/conversations',
        headers: bearer(alice),
        payload: { type: 'group', name: 'TestGroup', memberIds: [bob.id] },
      })
      groupId = res.json().id
    })

    it('non-admin cannot add member → 403', async () => {
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })
      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${groupId}/members`,
        headers: bearer(bob), // bob is member, not admin
        payload: { userId: charlie.id },
      })
      expect(res.statusCode).toBe(403)
    })

    it('admin can add member', async () => {
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })
      const res = await app.inject({
        method: 'POST', url: `/api/conversations/${groupId}/members`,
        headers: bearer(alice), // alice is admin
        payload: { userId: charlie.id },
      })
      expect(res.statusCode).toBe(201)
    })

    it('non-admin cannot remove other member → 403', async () => {
      const charlie = await registerUser(app, { username: 'charlie', email: 'charlie@t.com' })
      // Admin adds charlie
      await app.inject({
        method: 'POST', url: `/api/conversations/${groupId}/members`,
        headers: bearer(alice), payload: { userId: charlie.id },
      })
      // Bob (member) tries to remove charlie → 403
      const res = await app.inject({
        method: 'DELETE', url: `/api/conversations/${groupId}/members/${charlie.id}`,
        headers: bearer(bob),
      })
      expect(res.statusCode).toBe(403)
    })

    it('member can remove themselves (leave group)', async () => {
      const res = await app.inject({
        method: 'DELETE', url: `/api/conversations/${groupId}/members/${bob.id}`,
        headers: bearer(bob), // removing self
      })
      expect(res.statusCode).toBe(204)
    })

    it('non-admin cannot rename group → 403', async () => {
      const res = await app.inject({
        method: 'PATCH', url: `/api/conversations/${groupId}`,
        headers: bearer(bob),
        payload: { name: 'Hacked Name' },
      })
      expect(res.statusCode).toBe(403)
    })
  })
})
