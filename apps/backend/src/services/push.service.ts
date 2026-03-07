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
