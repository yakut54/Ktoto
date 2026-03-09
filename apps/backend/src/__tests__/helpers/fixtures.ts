import type { FastifyInstance } from 'fastify'

let _counter = 0
const uid = () => `u${Date.now()}${++_counter}`

export interface TestUser {
  id: string
  username: string
  email: string
  accessToken: string
  refreshToken: string
}

/** Register a fresh user and return tokens + id */
export async function registerUser(
  app: FastifyInstance,
  overrides: Partial<{ username: string; email: string; password: string }> = {},
): Promise<TestUser> {
  const tag = uid()
  const body = {
    username: overrides.username ?? `user_${tag}`,
    email: overrides.email ?? `user_${tag}@test.com`,
    password: overrides.password ?? 'password123',
  }

  const res = await app.inject({
    method: 'POST',
    url: '/api/auth/register',
    payload: body,
  })

  if (res.statusCode !== 201) {
    throw new Error(`registerUser failed: ${res.statusCode} ${res.body}`)
  }

  const data = res.json()
  return {
    id: data.user.id,
    username: data.user.username,
    email: data.user.email,
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
  }
}

/** Create a direct conversation between two users */
export async function createDirectConv(
  app: FastifyInstance,
  fromUser: TestUser,
  toUserId: string,
): Promise<string> {
  const res = await app.inject({
    method: 'POST',
    url: '/api/conversations',
    headers: { Authorization: `Bearer ${fromUser.accessToken}` },
    payload: { type: 'direct', userId: toUserId },
  })
  return res.json().id
}

/** Send a text message, return message object */
export async function sendMessage(
  app: FastifyInstance,
  user: TestUser,
  convId: string,
  content: string,
) {
  const res = await app.inject({
    method: 'POST',
    url: `/api/conversations/${convId}/messages`,
    headers: { Authorization: `Bearer ${user.accessToken}` },
    payload: { content, type: 'text' },
  })
  return res.json()
}

/** Auth header helper */
export const bearer = (user: TestUser) => ({
  Authorization: `Bearer ${user.accessToken}`,
})
