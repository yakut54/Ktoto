import { v4 as uuidv4 } from 'uuid'
import type { FastifyInstance } from 'fastify'

const ACCESS_EXPIRES = process.env.JWT_ACCESS_EXPIRES || '15m'
const REFRESH_EXPIRES_SEC = 7 * 24 * 60 * 60 // 7 days in seconds

export class TokenService {
  constructor(private app: FastifyInstance) {}

  generateAccessToken(userId: string): string {
    return this.app.jwt.sign(
      { userId, type: 'access' },
      { expiresIn: ACCESS_EXPIRES }
    )
  }

  async generateRefreshToken(userId: string): Promise<string> {
    const tokenId = uuidv4()
    const token = this.app.jwt.sign(
      { userId, type: 'refresh', jti: tokenId },
      { expiresIn: REFRESH_EXPIRES_SEC }
    )

    // Store in Redis: key = refresh:{tokenId}, value = userId
    await this.app.redis.setex(
      `refresh:${tokenId}`,
      REFRESH_EXPIRES_SEC,
      userId
    )

    return token
  }

  async validateRefreshToken(token: string): Promise<string> {
    let payload: { userId: string; type: string; jti: string }

    try {
      payload = this.app.jwt.verify(token) as typeof payload
    } catch {
      throw new Error('Invalid refresh token')
    }

    if (payload.type !== 'refresh') {
      throw new Error('Invalid token type')
    }

    const storedUserId = await this.app.redis.get(`refresh:${payload.jti}`)
    if (!storedUserId) {
      throw new Error('Refresh token expired or revoked')
    }

    return payload.userId
  }

  async revokeRefreshToken(token: string): Promise<void> {
    try {
      const payload = this.app.jwt.verify(token) as { jti: string }
      await this.app.redis.del(`refresh:${payload.jti}`)
    } catch {
      // Token already invalid, ignore
    }
  }

  async revokeAllUserTokens(userId: string): Promise<void> {
    const keys = await this.app.redis.keys(`refresh:*`)
    for (const key of keys) {
      const stored = await this.app.redis.get(key)
      if (stored === userId) {
        await this.app.redis.del(key)
      }
    }
  }
}
