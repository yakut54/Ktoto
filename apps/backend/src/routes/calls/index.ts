import type { FastifyInstance } from 'fastify'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'

const LOG_DIR = '/logs'
const LOG_FILE = path.join(LOG_DIR, 'call-debug.log')

try { fs.mkdirSync(LOG_DIR, { recursive: true }) } catch { /* ok */ }

function appendLog(entry: object) {
  try {
    fs.appendFileSync(LOG_FILE, JSON.stringify(entry) + '\n')
  } catch { /* never crash on log write */ }
}

export async function callRoutes(app: FastifyInstance) {
  /**
   * POST /api/calls/log
   * Android remote logger — no auth, fire-and-forget
   */
  app.post('/log', async (request, reply) => {
    const body = request.body as {
      level?: string
      tag?: string
      message?: string
      data?: Record<string, unknown>
      deviceId?: string
      ts?: number
    }
    const entry = {
      source: 'android',
      deviceId: body.deviceId ?? 'unknown',
      tag: body.tag ?? '?',
      level: body.level ?? 'info',
      message: body.message ?? '',
      data: body.data,
      ts: body.ts ?? Date.now(),
      receivedAt: Date.now(),
    }
    appendLog(entry)
    const logFn = entry.level === 'error' ? app.log.error : entry.level === 'warn' ? app.log.warn : app.log.info
    logFn.call(app.log, entry, `[android/${entry.deviceId}] [${entry.tag}] ${entry.message}`)
    return reply.send({ ok: true })
  })
  /**
   * GET /api/calls/history
   * Returns the authenticated user's call history (last 100 calls).
   */
  app.get(
    '/history',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const userId = (request.user as { userId: string }).userId
      const { rows } = await app.pg.query<{
        id: string
        caller_id: string
        callee_id: string
        call_type: string
        end_reason: string | null
        duration_sec: number | null
        started_at: string
        answered_at: string | null
        caller_username: string
        caller_avatar: string | null
        callee_username: string
        callee_avatar: string | null
      }>(
        `SELECT c.id, c.caller_id, c.callee_id, c.call_type, c.end_reason, c.duration_sec,
                c.started_at, c.answered_at,
                u1.username AS caller_username, u1.avatar_url AS caller_avatar,
                u2.username AS callee_username, u2.avatar_url AS callee_avatar
         FROM calls c
         JOIN users u1 ON u1.id = c.caller_id
         JOIN users u2 ON u2.id = c.callee_id
         WHERE c.caller_id = $1 OR c.callee_id = $1
         ORDER BY c.started_at DESC
         LIMIT 100`,
        [userId],
      )
      return reply.send(
        rows.map((r) => ({
          id: r.id,
          callType: r.call_type,
          endReason: r.end_reason,
          durationSec: r.duration_sec,
          startedAt: r.started_at,
          answeredAt: r.answered_at,
          isOutgoing: r.caller_id === userId,
          peer:
            r.caller_id === userId
              ? { id: r.callee_id, username: r.callee_username, avatarUrl: r.callee_avatar }
              : { id: r.caller_id, username: r.caller_username, avatarUrl: r.caller_avatar },
        })),
      )
    },
  )

  /**
   * GET /api/calls/turn-credentials
   * Returns short-lived TURN credentials (HMAC-SHA1, TTL 24h).
   * Android uses these to configure PeerConnection ICE servers.
   */
  app.get(
    '/turn-credentials',
    { preHandler: [app.authenticate] },
    async (request, reply) => {
      const secret = process.env.TURN_SECRET
      if (!secret) {
        return reply.status(503).send({ error: 'TURN not configured' })
      }

      const ttl = 24 * 3600 // 24 hours
      const timestamp = Math.floor(Date.now() / 1000) + ttl
      const userId = (request.user as { userId: string }).userId
      const username = `${timestamp}:${userId}`
      const credential = crypto
        .createHmac('sha1', secret)
        .update(username)
        .digest('base64')

      const host = process.env.TURN_HOST || '31.128.39.216'

      return reply.send({
        ttl,
        iceServers: [
          // Own STUN
          { urls: `stun:${host}:3478` },
          // Own TURN UDP
          { urls: `turn:${host}:3478`, username, credential },
          // Own TURN TCP (firewall fallback)
          { urls: `turn:${host}:3478?transport=tcp`, username, credential },
          // Own TURNS TLS on 5349 (corporate firewall fallback)
          { urls: `turns:${host}:5349?transport=tcp`, username, credential },
          // Google STUN as last-resort discovery only
          { urls: 'stun:stun.l.google.com:19302' },
        ],
      })
    },
  )
}
