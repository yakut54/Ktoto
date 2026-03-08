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
