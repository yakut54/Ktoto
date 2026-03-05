const NTFY_URL = process.env.NTFY_URL || 'http://ktoto_ntfy:80'
const NTFY_USER = process.env.NTFY_USER || 'ktoto-backend'
const NTFY_PASS = process.env.NTFY_PASS || 'ntfy-backend-2026'

const auth = Buffer.from(`${NTFY_USER}:${NTFY_PASS}`).toString('base64')

export async function pushToUser(userId: string, title: string, message: string) {
  try {
    await fetch(`${NTFY_URL}/ktoto-${userId}`, {
      method: 'POST',
      headers: {
        Authorization: `Basic ${auth}`,
        Title: title,
        Priority: 'default',
        'Content-Type': 'text/plain',
      },
      body: message,
    })
  } catch {
    // Non-critical — don't fail message sending if push fails
  }
}
