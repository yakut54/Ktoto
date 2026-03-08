import admin from '../config/firebase.js'

type QueryFn = (sql: string, params: unknown[]) => Promise<{ rows: Record<string, unknown>[] }>

export async function pushCallToUser(
  query: QueryFn,
  userId: string,
  data: {
    callId: string
    fromUserId: string
    fromUsername: string
    fromAvatarUrl: string | null
    callType: string
  },
) {
  const { rows } = await query('SELECT fcm_token FROM users WHERE id=$1', [userId])
  const token = (rows[0] as { fcm_token?: string | null })?.fcm_token
  if (!token) return
  try {
    await admin.messaging().send({
      token,
      data: {
        type: 'incoming_call',
        callId: data.callId,
        fromUserId: data.fromUserId,
        fromUsername: data.fromUsername,
        fromAvatarUrl: data.fromAvatarUrl || '',
        callType: data.callType,
      },
      android: { priority: 'high', ttl: 30000 },
    })
  } catch {
    // Non-critical
  }
}

const NTFY_URL = process.env.NTFY_URL || 'http://ktoto_ntfy:80'
const NTFY_USER = process.env.NTFY_USER || 'ktoto-backend'
const NTFY_PASS = process.env.NTFY_PASS || 'ntfy-backend-2026'

const auth = Buffer.from(`${NTFY_USER}:${NTFY_PASS}`).toString('base64')

export async function pushToUser(userId: string, title: string, message: string, conversationId?: string) {
  try {
    const headers: Record<string, string> = {
      Authorization: `Basic ${auth}`,
      Title: title,
      Priority: 'default',
      'Content-Type': 'text/plain',
    }
    if (conversationId) {
      headers['Click'] = `ktoto://chat/${conversationId}`
    }
    await fetch(`${NTFY_URL}/ktoto-${userId}`, {
      method: 'POST',
      headers,
      body: message,
    })
  } catch {
    // Non-critical — don't fail message sending if push fails
  }
}
