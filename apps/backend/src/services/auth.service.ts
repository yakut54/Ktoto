import * as argon2 from 'argon2'
import type { FastifyInstance } from 'fastify'
import type { QueryResult } from 'pg'

export interface User {
  id: string
  username: string
  email: string
  avatar_url: string | null
  role: string
  banned_at: Date | null
  created_at: Date
}

export interface UserWithPassword extends User {
  password_hash: string
}

export class AuthService {
  constructor(private app: FastifyInstance) {}

  async register(data: {
    username: string
    email: string
    password: string
  }): Promise<User> {
    const client = await this.app.pg.connect()

    try {
      // Check if email or username already exists
      const existing = await client.query<{ id: string }>(
        'SELECT id FROM users WHERE email = $1 OR username = $2 LIMIT 1',
        [data.email.toLowerCase(), data.username.toLowerCase()]
      )

      if (existing.rows.length > 0) {
        throw new Error('User with this email or username already exists')
      }

      const passwordHash = await argon2.hash(data.password)

      const result: QueryResult<User> = await client.query(
        `INSERT INTO users (username, email, password_hash)
         VALUES ($1, $2, $3)
         RETURNING id, username, email, avatar_url, role, banned_at, created_at`,
        [data.username.toLowerCase(), data.email.toLowerCase(), passwordHash]
      )

      return result.rows[0]
    } finally {
      client.release()
    }
  }

  async login(data: {
    username: string
    password: string
  }): Promise<User> {
    const client = await this.app.pg.connect()

    try {
      const result: QueryResult<UserWithPassword> = await client.query(
        `SELECT id, username, email, avatar_url, role, banned_at, created_at, password_hash
         FROM users WHERE username = $1 LIMIT 1`,
        [data.username.toLowerCase()]
      )

      if (result.rows.length === 0) {
        throw new Error('Invalid credentials')
      }

      const user = result.rows[0]

      if (user.banned_at) {
        throw new Error('Account is banned')
      }

      const valid = await argon2.verify(user.password_hash, data.password)

      if (!valid) {
        throw new Error('Invalid credentials')
      }

      // Update last_seen_at
      await client.query(
        'UPDATE users SET last_seen_at = NOW() WHERE id = $1',
        [user.id]
      )

      const { password_hash: _, ...userWithoutPassword } = user
      return userWithoutPassword
    } finally {
      client.release()
    }
  }

  async getById(userId: string): Promise<User | null> {
    const client = await this.app.pg.connect()

    try {
      const result: QueryResult<User> = await client.query(
        `SELECT id, username, email, avatar_url, role, banned_at, created_at
         FROM users WHERE id = $1 LIMIT 1`,
        [userId]
      )

      return result.rows[0] ?? null
    } finally {
      client.release()
    }
  }
}
