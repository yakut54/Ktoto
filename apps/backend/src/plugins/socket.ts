import fp from 'fastify-plugin'
import { Server, Socket } from 'socket.io'
import type { FastifyInstance } from 'fastify'
import { randomUUID } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { pushCallToUser } from '../services/push.service.js'

const LOG_FILE = path.join('/logs', 'call-debug.log')
try { fs.mkdirSync('/logs', { recursive: true }) } catch { /* ok */ }

function serverLog(event: string, data: object) {
  try {
    fs.appendFileSync(LOG_FILE, JSON.stringify({ source: 'server', event, ...data, ts: Date.now() }) + '\n')
  } catch { /* never crash */ }
}

// ─── Call session types ────────────────────────────────────────────────────

type CallState = 'ringing' | 'negotiating' | 'active' | 'ended'

interface CallSession {
  id: string
  callerId: string
  calleeId: string
  callType: 'audio' | 'video'
  state: CallState
  createdAt: number
  answeredAt?: number
  endedAt?: number
  endReason?: string
  reconnectCount: number
  lastHeartbeat: number
}

// ─── Rate limiter (call_initiate) ──────────────────────────────────────────

const callRateLimit = new Map<string, { count: number; resetAt: number }>()

function checkCallRateLimit(userId: string): boolean {
  const now = Date.now()
  const entry = callRateLimit.get(userId)
  if (!entry || now > entry.resetAt) {
    callRateLimit.set(userId, { count: 1, resetAt: now + 60_000 })
    return true
  }
  if (entry.count >= 10) return false
  entry.count++
  return true
}

// ─── Redis helpers ─────────────────────────────────────────────────────────

const CALL_TTL = 3600 // 1h

async function saveCall(redis: FastifyInstance['redis'], call: CallSession) {
  await redis.set(`call:${call.id}`, JSON.stringify(call), 'EX', CALL_TTL)
  await redis.set(`user_call:${call.callerId}`, call.id, 'EX', CALL_TTL)
  await redis.set(`user_call:${call.calleeId}`, call.id, 'EX', CALL_TTL)
}

async function getCall(
  redis: FastifyInstance['redis'],
  callId: string,
): Promise<CallSession | null> {
  const raw = await redis.get(`call:${callId}`)
  return raw ? (JSON.parse(raw) as CallSession) : null
}

async function getActiveCallForUser(
  redis: FastifyInstance['redis'],
  userId: string,
): Promise<CallSession | null> {
  const callId = await redis.get(`user_call:${userId}`)
  if (!callId) return null
  const call = await getCall(redis, callId)
  if (!call || call.state === 'ended') {
    await redis.del(`user_call:${userId}`)
    return null
  }
  return call
}

async function endCall(
  redis: FastifyInstance['redis'],
  callId: string,
  reason: string,
): Promise<CallSession | undefined> {
  const call = await getCall(redis, callId)
  if (!call) return undefined
  const updated: CallSession = {
    ...call,
    state: 'ended',
    endedAt: Date.now(),
    endReason: reason,
  }
  await redis.set(`call:${callId}`, JSON.stringify(updated), 'EX', 600)
  await redis.del(`user_call:${call.callerId}`)
  await redis.del(`user_call:${call.calleeId}`)
  return updated
}

async function persistCallToDB(
  app: FastifyInstance,
  call: CallSession,
  durationSec?: number,
) {
  try {
    await app.pg.query(
      `INSERT INTO calls (id, caller_id, callee_id, call_type, end_reason, duration_sec, started_at, answered_at, ended_at)
       VALUES ($1,$2,$3,$4,$5,$6, to_timestamp($7/1000.0), to_timestamp($8/1000.0), to_timestamp($9/1000.0))
       ON CONFLICT (id) DO NOTHING`,
      [
        call.id,
        call.callerId,
        call.calleeId,
        call.callType,
        call.endReason ?? 'unknown',
        durationSec ?? null,
        call.createdAt,
        call.answeredAt ?? null,
        call.endedAt ?? Date.now(),
      ],
    )
  } catch (e) {
    app.log.error(e, 'persistCallToDB failed')
  }
}

async function insertCallMessage(
  app: FastifyInstance,
  call: CallSession,
  durationSec?: number,
) {
  try {
    const convRes = await app.pg.query<{ id: string }>(
      `SELECT c.id FROM conversations c
       JOIN conversation_participants cp1 ON cp1.conversation_id = c.id AND cp1.user_id = $1
       JOIN conversation_participants cp2 ON cp2.conversation_id = c.id AND cp2.user_id = $2
       WHERE c.type = 'direct'
       LIMIT 1`,
      [call.callerId, call.calleeId],
    )
    if (!convRes.rows[0]) return
    const convId = convRes.rows[0].id

    const answered = call.answeredAt != null
    const reason = call.endReason ?? 'unknown'
    const outcome = answered ? 'completed'
      : reason === 'declined' ? 'declined'
      : reason === 'cancelled' ? 'cancelled'
      : 'missed'

    const content = JSON.stringify({ callType: call.callType, duration: durationSec ?? null, outcome })

    const callerRes = await app.pg.query<{ username: string; avatar_url: string | null }>(
      'SELECT username, avatar_url FROM users WHERE id=$1', [call.callerId],
    )
    if (!callerRes.rows[0]) return

    const msgRes = await app.pg.query<{ id: string; created_at: string }>(
      `INSERT INTO messages (conversation_id, user_id, type, content) VALUES ($1,$2,'call',$3) RETURNING id, created_at`,
      [convId, call.callerId, content],
    )
    if (!msgRes.rows[0]) return

    const payload = {
      id: msgRes.rows[0].id,
      content,
      type: 'call',
      createdAt: msgRes.rows[0].created_at,
      replyToId: null,
      sender: { id: call.callerId, username: callerRes.rows[0].username, avatarUrl: callerRes.rows[0].avatar_url },
      conversationId: convId,
      attachment: null,
      isDelivered: true,
    }

    const parts = await app.pg.query<{ user_id: string }>(
      'SELECT user_id FROM conversation_participants WHERE conversation_id=$1', [convId],
    )
    for (const p of parts.rows) {
      app.io.to(`user:${p.user_id}`).emit('new_message', payload)
    }
  } catch (e) {
    app.log.error(e, 'insertCallMessage failed')
  }
}

// ─── ICE candidate buffer ──────────────────────────────────────────────────

const iceBuffer = new Map<string, Array<{ fromUserId: string; candidate: unknown }>>()

// ─── Watchdog (cleans up stale calls every 30s) ────────────────────────────

function startCallWatchdog(app: FastifyInstance) {
  setInterval(async () => {
    try {
      const keys = await app.redis.keys('call:*')
      for (const key of keys) {
        const raw = await app.redis.get(key)
        if (!raw) continue
        const call = JSON.parse(raw) as CallSession
        if (call.state === 'ended') continue
        const age = Date.now() - call.createdAt

        let reason: string | null = null
        if (call.state === 'ringing' && age > 35_000) {
          reason = 'timeout_no_answer'
        } else if (call.state === 'negotiating' && age > 30_000) {
          reason = 'timeout_negotiation'
        } else if (
          call.state === 'active' &&
          Date.now() - call.lastHeartbeat > 90_000
        ) {
          reason = 'timeout_no_heartbeat'
        }

        if (reason) {
          app.log.info({ callId: call.id, reason }, 'Watchdog ending stale call')
          const endedWatchdog = await endCall(app.redis, call.id, reason)
          if (endedWatchdog) await persistCallToDB(app, endedWatchdog)
          const payload = { callId: call.id, reason }
          app.io.to(`user:${call.callerId}`).emit('call_force_end', payload)
          app.io.to(`user:${call.calleeId}`).emit('call_force_end', payload)
        }
      }
    } catch (e) {
      app.log.error(e, 'Call watchdog error')
    }
  }, 30_000)
}

// ─── Per-socket call handlers ──────────────────────────────────────────────

function registerCallHandlers(
  app: FastifyInstance,
  socket: Socket,
  userId: string,
) {
  const redis = app.redis

  // ── call_initiate ────────────────────────────────────────────────────────
  socket.on(
    'call_initiate',
    async (data: { toUserId: string; callType: 'audio' | 'video' }) => {
      app.log.info({ from: userId, data }, '>>> call_initiate received')
      serverLog('call_initiate', { from: userId, toUserId: data?.toUserId, callType: data?.callType })

      if (!checkCallRateLimit(userId)) {
        app.log.warn({ userId }, 'call_initiate rate limited')
        socket.emit('call_error', { reason: 'rate_limited', retryAfter: 60 })
        return
      }

      const { toUserId, callType } = data
      if (!toUserId || !callType) {
        app.log.warn({ data }, 'call_initiate missing toUserId or callType')
        return
      }

      // Check caller not already in a call
      const myCall = await getActiveCallForUser(redis, userId)
      if (myCall) {
        app.log.warn({ userId, callId: myCall.id }, 'call_initiate: caller is busy')
        socket.emit('call_error', { reason: 'you_are_busy' })
        return
      }

      // Check callee not already in a call
      const theirCall = await getActiveCallForUser(redis, toUserId)
      if (theirCall) {
        app.log.warn({ toUserId, callId: theirCall.id }, 'call_initiate: callee is busy')
        socket.emit('call_busy', { toUserId })
        return
      }

      // Check callee socket room
      const calleeSockets = await app.io.in(`user:${toUserId}`).fetchSockets()
      app.log.info({ toUserId, socketsOnline: calleeSockets.length }, 'call_initiate: callee socket check')

      // Symmetric race: if they're calling us simultaneously, lower userId wins
      // (handled by checking active call above — first one in wins)

      // Fetch caller info
      const { rows } = await app.pg.query<{
        username: string
        avatar_url: string | null
      }>('SELECT username, avatar_url FROM users WHERE id=$1', [userId])
      const caller = rows[0]
      if (!caller) return

      const call: CallSession = {
        id: randomUUID(),
        callerId: userId,
        calleeId: toUserId,
        callType,
        state: 'ringing',
        createdAt: Date.now(),
        reconnectCount: 0,
        lastHeartbeat: Date.now(),
      }

      await saveCall(redis, call)

      // Emit to callee (all their devices)
      app.io.to(`user:${toUserId}`).emit('call_incoming', {
        callId: call.id,
        fromUserId: userId,
        fromUsername: caller.username,
        fromAvatarUrl: caller.avatar_url,
        callType,
      })

      // FCM high-priority push to wake device if WebSocket is dead (MIUI/OEM kill)
      pushCallToUser(
        (sql, params) => app.pg.query(sql, params as unknown[]),
        toUserId,
        { callId: call.id, fromUsername: caller.username, fromAvatarUrl: caller.avatar_url, callType },
      ).catch(() => {})

      // Confirm to caller
      socket.emit('call_initiated', { callId: call.id })
      app.log.info({ callId: call.id, callerId: userId, calleeId: toUserId, callType }, 'Call initiated → call_incoming sent to callee')
      serverLog('call_initiated', { callId: call.id, callerId: userId, calleeId: toUserId, callType })
    },
  )

  // ── call_ringing (callee → caller) ───────────────────────────────────────
  socket.on('call_ringing', async (data: { callId: string }) => {
    app.log.info({ callId: data.callId, from: userId }, '>>> call_ringing received')
    serverLog('call_ringing', { callId: data.callId, from: userId })
    const call = await getCall(redis, data.callId)
    if (!call || call.calleeId !== userId) return
    app.io.to(`user:${call.callerId}`).emit('call_ringing', { callId: call.id })
  })

  // ── call_offer (caller → callee, also used for ICE restart) ──────────────
  socket.on(
    'call_offer',
    async (data: { callId: string; sdp: { type: string; sdp: string } }) => {
      app.log.info({ callId: data.callId, from: userId }, '>>> call_offer received')
      const call = await getCall(redis, data.callId)
      if (!call || call.callerId !== userId) return
      if (call.state === 'ended') return

      if (call.state === 'ringing') {
        const updated = { ...call, state: 'negotiating' as CallState }
        await saveCall(redis, updated)
      }

      app.log.info({ callId: call.id, toUserId: call.calleeId }, 'call_offer → forwarded to callee')
      serverLog('call_offer_forwarded', { callId: call.id, from: userId, toUserId: call.calleeId })
      app.io
        .to(`user:${call.calleeId}`)
        .emit('call_offer', { callId: call.id, sdp: data.sdp })
    },
  )

  // ── call_answer (callee → caller) ────────────────────────────────────────
  socket.on(
    'call_answer',
    async (data: { callId: string; sdp: { type: string; sdp: string } }) => {
      app.log.info({ callId: data.callId, from: userId }, '>>> call_answer received')
      const call = await getCall(redis, data.callId)
      if (!call || call.calleeId !== userId) return
      if (call.state === 'ended') return

      const updated: CallSession = {
        ...call,
        state: 'active',
        answeredAt: Date.now(),
        lastHeartbeat: Date.now(),
      }
      await saveCall(redis, updated)

      // Cancel other devices of callee
      const otherSockets = await app.io.in(`user:${userId}`).fetchSockets()
      for (const s of otherSockets) {
        if (s.id !== socket.id) {
          s.emit('call_force_end', {
            callId: call.id,
            reason: 'other_device_answered',
          })
        }
      }

      app.io
        .to(`user:${call.callerId}`)
        .emit('call_answer', { callId: call.id, sdp: data.sdp })

      // Flush buffered ICE candidates
      const buffered = iceBuffer.get(call.id) ?? []
      iceBuffer.delete(call.id)
      for (const item of buffered) {
        const target =
          item.fromUserId === call.callerId ? call.calleeId : call.callerId
        app.io
          .to(`user:${target}`)
          .emit('call_ice_candidate', { callId: call.id, candidate: item.candidate })
      }

      app.log.info({ callId: call.id, bufferedCandidates: buffered.length }, 'Call answered → state=active, ICE buffer flushed')
      serverLog('call_answer_forwarded', { callId: call.id, from: userId, toUserId: call.callerId, bufferedCandidates: buffered.length })
    },
  )

  // ── call_ice_candidate ────────────────────────────────────────────────────
  socket.on(
    'call_ice_candidate',
    async (data: { callId: string; candidate: unknown }) => {
      const call = await getCall(redis, data.callId)
      if (!call) return
      if (call.callerId !== userId && call.calleeId !== userId) return
      if (call.state === 'ended') return

      const toUserId =
        call.callerId === userId ? call.calleeId : call.callerId

      // If call not yet active, buffer candidates
      if (call.state === 'ringing' || call.state === 'negotiating') {
        const buf = iceBuffer.get(call.id) ?? []
        buf.push({ fromUserId: userId, candidate: data.candidate })
        iceBuffer.set(call.id, buf)
        return
      }

      app.io
        .to(`user:${toUserId}`)
        .emit('call_ice_candidate', { callId: call.id, candidate: data.candidate })
    },
  )

  // ── call_reject (callee → caller) ────────────────────────────────────────
  socket.on(
    'call_reject',
    async (data: { callId: string; reason?: string }) => {
      const call = await getCall(redis, data.callId)
      if (!call || call.calleeId !== userId) return
      if (call.state === 'ended') return

      const endedReject = await endCall(redis, call.id, data.reason ?? 'declined')
      iceBuffer.delete(call.id)
      if (endedReject) {
        await persistCallToDB(app, endedReject)
        await insertCallMessage(app, endedReject)
      }
      app.io.to(`user:${call.callerId}`).emit('call_rejected', {
        callId: call.id,
        reason: data.reason ?? 'declined',
      })
    },
  )

  // ── call_cancel (caller → callee) ────────────────────────────────────────
  socket.on(
    'call_cancel',
    async (data: { callId: string; reason?: string }) => {
      const call = await getCall(redis, data.callId)
      if (!call || call.callerId !== userId) return
      if (call.state === 'ended') return

      const endedCancel = await endCall(redis, call.id, data.reason ?? 'cancelled')
      iceBuffer.delete(call.id)
      if (endedCancel) {
        await persistCallToDB(app, endedCancel)
        await insertCallMessage(app, endedCancel)
      }
      app.io.to(`user:${call.calleeId}`).emit('call_cancelled', {
        callId: call.id,
        reason: data.reason ?? 'cancelled',
      })
    },
  )

  // ── call_end (either party) ───────────────────────────────────────────────
  socket.on(
    'call_end',
    async (data: { callId: string; duration?: number; reason?: string }) => {
      const call = await getCall(redis, data.callId)
      if (!call) return
      if (call.callerId !== userId && call.calleeId !== userId) return
      if (call.state === 'ended') return

      const reason = data.reason ?? 'normal'
      const ended = await endCall(redis, call.id, reason)
      iceBuffer.delete(call.id)
      if (ended) {
        await persistCallToDB(app, ended, data.duration)
        await insertCallMessage(app, ended, data.duration)
      }

      const toUserId =
        call.callerId === userId ? call.calleeId : call.callerId
      app.io.to(`user:${toUserId}`).emit('call_ended', {
        callId: call.id,
        reason,
        duration: data.duration,
      })
      app.log.info({ callId: call.id, reason, duration: data.duration }, 'Call ended')
    },
  )

  // ── call_mute (relay to peer) ─────────────────────────────────────────────
  socket.on('call_mute', async (data: { callId: string; muted: boolean }) => {
    const call = await getCall(redis, data.callId)
    if (!call) return
    if (call.callerId !== userId && call.calleeId !== userId) return

    const toUserId = call.callerId === userId ? call.calleeId : call.callerId
    app.io
      .to(`user:${toUserId}`)
      .emit('call_mute', { callId: call.id, muted: data.muted, fromUserId: userId })
  })

  // ── call_video_toggle (relay to peer) ────────────────────────────────────
  socket.on(
    'call_video_toggle',
    async (data: { callId: string; enabled: boolean }) => {
      const call = await getCall(redis, data.callId)
      if (!call) return
      if (call.callerId !== userId && call.calleeId !== userId) return

      const toUserId = call.callerId === userId ? call.calleeId : call.callerId
      app.io
        .to(`user:${toUserId}`)
        .emit('call_video_toggle', { callId: call.id, enabled: data.enabled, fromUserId: userId })
    },
  )

  // ── call_heartbeat ────────────────────────────────────────────────────────
  socket.on('call_heartbeat', async (data: { callId: string }) => {
    const call = await getCall(redis, data.callId)
    if (!call) return
    if (call.callerId !== userId && call.calleeId !== userId) return
    const updated = { ...call, lastHeartbeat: Date.now() }
    await saveCall(redis, updated)
  })

  // ── call_state_sync (after socket reconnect) ──────────────────────────────
  socket.on(
    'call_state_sync',
    async (data: { callId?: string; localState?: string }) => {
      const serverCall = await getActiveCallForUser(redis, userId)

      // Client thinks call is active, server has nothing
      if (data.callId && !serverCall) {
        socket.emit('call_force_end', { callId: data.callId, reason: 'server_no_call' })
        return
      }

      // Client thinks no call, server thinks active
      if (!data.callId && serverCall && serverCall.state === 'active') {
        socket.emit('call_rejoin', { callId: serverCall.id, callType: serverCall.callType })
        return
      }

      // Both agree on the call
      if (data.callId && serverCall && data.callId === serverCall.id) {
        socket.emit('call_state_confirmed', { call: serverCall })

        // Flush buffered candidates if any
        const buffered = iceBuffer.get(serverCall.id) ?? []
        iceBuffer.delete(serverCall.id)
        for (const item of buffered) {
          socket.emit('call_ice_candidate', {
            callId: serverCall.id,
            candidate: item.candidate,
          })
        }
      }
    },
  )
}

// ─── Main plugin ───────────────────────────────────────────────────────────

export const socketPlugin = fp(async (app: FastifyInstance) => {
  const io = new Server(app.server, {
    cors: {
      origin: process.env.CORS_ORIGIN?.split(',') || '*',
      credentials: true,
    },
  })

  // JWT auth middleware
  io.use((socket, next) => {
    const token = socket.handshake.auth.token as string | undefined
    if (!token) {
      return next(new Error('Authentication required'))
    }
    try {
      const payload = app.jwt.verify<{ userId: string; type: string }>(token)
      if (payload.type !== 'access') {
        return next(new Error('Invalid token type'))
      }
      socket.data.userId = payload.userId
      next()
    } catch {
      next(new Error('Invalid token'))
    }
  })

  io.on('connection', async (socket) => {
    const { userId } = socket.data as { userId: string }
    app.log.info({ userId }, 'Socket connected')
    serverLog('socket_connected', { userId })

    // Personal room — for delivering messages to a specific user
    socket.join(`user:${userId}`)

    // Mark online in DB
    await app.pg.query(`UPDATE users SET status='online' WHERE id=$1`, [userId])

    // Fetch all conversation partners once — reused in disconnect handler
    const { rows: partners } = await app.pg.query<{ user_id: string }>(
      `SELECT DISTINCT cp2.user_id
       FROM conversation_participants cp1
       JOIN conversation_participants cp2 ON cp2.conversation_id = cp1.conversation_id AND cp2.user_id != $1
       WHERE cp1.user_id = $1`,
      [userId],
    )

    // Notify partners: this user is online
    for (const p of partners) {
      app.io.to(`user:${p.user_id}`).emit('user_status', { userId, status: 'online' })
    }

    // typing: { conversationId } → broadcast to other participants
    socket.on('typing', async ({ conversationId }: { conversationId: string }) => {
      const { rows } = await app.pg.query<{ user_id: string }>(
        `SELECT user_id FROM conversation_participants WHERE conversation_id=$1 AND user_id!=$2`,
        [conversationId, userId],
      )
      for (const p of rows) {
        app.io.to(`user:${p.user_id}`).emit('typing', { conversationId, userId })
      }
    })

    // Register call signaling handlers
    registerCallHandlers(app, socket, userId)

    // ── Sync call state after reconnect ─────────────────────────────────
    // Auto-clean stale ringing sessions left by crashed caller (no point keeping
    // a ringing session where the caller just (re)connected — they clearly crashed)
    const maybeStaleCall = await getActiveCallForUser(app.redis, userId)
    if (maybeStaleCall) {
      if (maybeStaleCall.state === 'ringing' && maybeStaleCall.callerId === userId) {
        app.log.info({ callId: maybeStaleCall.id, userId }, 'Auto-cleaning stale ringing call on reconnect')
        await endCall(app.redis, maybeStaleCall.id, 'caller_reconnected')
        // Notify callee that call was cancelled (in case they got the incoming event)
        app.io.to(`user:${maybeStaleCall.calleeId}`).emit('call_cancelled', {
          callId: maybeStaleCall.id,
          reason: 'caller_reconnected',
        })
      } else {
        socket.emit('call_state_confirmed', { call: maybeStaleCall })
      }
    }

    socket.on('disconnect', async (reason) => {
      app.log.info({ userId, reason }, 'Socket disconnected')
      serverLog('socket_disconnected', { userId, reason })

      // Mark offline + update last_seen_at
      const { rows } = await app.pg.query<{ last_seen_at: string }>(
        `UPDATE users SET status='offline', last_seen_at=NOW() WHERE id=$1 RETURNING last_seen_at`,
        [userId],
      )
      const lastSeenAt = rows[0]?.last_seen_at

      // Notify partners: this user is offline
      for (const p of partners) {
        app.io.to(`user:${p.user_id}`).emit('user_status', { userId, status: 'offline', lastSeenAt })
      }

      // Handle active call disconnect — give 20s grace for reconnect
      const call = await getActiveCallForUser(app.redis, userId)
      if (call) {
        setTimeout(async () => {
          // Re-check — maybe user reconnected
          const stillActive = await getCall(app.redis, call.id)
          if (!stillActive || stillActive.state === 'ended') return

          // Check if user is back online (has active socket)
          const sockets = await app.io.in(`user:${userId}`).fetchSockets()
          if (sockets.length > 0) return // reconnected

          // End the call
          await endCall(app.redis, call.id, 'participant_disconnected')
          iceBuffer.delete(call.id)
          const otherId = call.callerId === userId ? call.calleeId : call.callerId
          app.io.to(`user:${otherId}`).emit('call_force_end', {
            callId: call.id,
            reason: 'connection_lost',
          })
        }, 20_000)
      }
    })
  })

  app.decorate('io', io)

  app.addHook('onClose', async () => {
    await io.close()
  })

  // Start watchdog after app is ready
  app.addHook('onReady', () => {
    startCallWatchdog(app)
  })
})

declare module 'fastify' {
  interface FastifyInstance {
    io: Server
  }
}
