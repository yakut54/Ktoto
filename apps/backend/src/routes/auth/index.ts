import { randomBytes, createHash } from 'crypto'
import * as argon2 from 'argon2'
import type { FastifyInstance } from 'fastify'
import { AuthService } from '../../services/auth.service.js'
import { TokenService } from '../../services/token.service.js'
import { EmailService } from '../../services/email.service.js'
import { z } from 'zod'

const registerSchema = z.object({
  username: z.string().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/),
  email: z.string().email(),
  password: z.string().min(8).max(100),
})

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
})

const refreshSchema = z.object({
  refreshToken: z.string().min(1),
})

export async function authRoutes(app: FastifyInstance) {
  const authService = new AuthService(app)
  const tokenService = new TokenService(app)
  const emailService = new EmailService()

  // POST /api/auth/register
  app.post('/register', async (request, reply) => {
    const parsed = registerSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: parsed.error.flatten() })
    }

    try {
      const user = await authService.register(parsed.data)
      const accessToken = tokenService.generateAccessToken(user.id, user.role)
      const refreshToken = await tokenService.generateRefreshToken(user.id)

      return reply.status(201).send({ user, accessToken, refreshToken })
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Registration failed'
      return reply.status(409).send({ error: message })
    }
  })

  // POST /api/auth/login
  app.post('/login', async (request, reply) => {
    const parsed = loginSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: parsed.error.flatten() })
    }

    try {
      const user = await authService.login(parsed.data)
      const accessToken = tokenService.generateAccessToken(user.id, user.role)
      const refreshToken = await tokenService.generateRefreshToken(user.id)

      return { user, accessToken, refreshToken }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Invalid credentials'
      const status = message === 'Account is banned' ? 403 : 401
      return reply.status(status).send({ error: message })
    }
  })

  // POST /api/auth/refresh
  app.post('/refresh', async (request, reply) => {
    const parsed = refreshSchema.safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: 'refreshToken required' })
    }

    try {
      const userId = await tokenService.validateRefreshToken(parsed.data.refreshToken)
      await tokenService.revokeRefreshToken(parsed.data.refreshToken) // Rotation

      const user = await authService.getById(userId)
      const role = user?.role ?? 'user'

      const accessToken = tokenService.generateAccessToken(userId, role)
      const newRefreshToken = await tokenService.generateRefreshToken(userId)

      return { accessToken, refreshToken: newRefreshToken }
    } catch {
      return reply.status(401).send({ error: 'Invalid or expired refresh token' })
    }
  })

  // POST /api/auth/logout
  app.post('/logout', { preHandler: [app.authenticate] }, async (request, reply) => {
    const parsed = refreshSchema.safeParse(request.body)
    if (parsed.success) {
      await tokenService.revokeRefreshToken(parsed.data.refreshToken)
    }
    return reply.status(204).send()
  })

  // GET /api/auth/me
  app.get('/me', { preHandler: [app.authenticate] }, async (request, reply) => {
    const user = await authService.getById(request.user.userId)
    if (!user) {
      return reply.status(404).send({ error: 'User not found' })
    }
    const avatarUrl = (user as any).avatar_url
      ? await app.s3.presignedUrl((user as any).avatar_url)
      : null
    return {
      user: {
        id: user.id,
        username: user.username,
        email: user.email,
        avatarUrl,
      },
    }
  })

  // POST /api/auth/forgot-password
  app.post('/forgot-password', async (request, reply) => {
    const parsed = z.object({ email: z.string().email() }).safeParse(request.body)
    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid email' })
    }

    // Always return 200 — don't reveal whether email exists
    try {
      const { rows } = await app.pg.query<{ id: string }>(
        'SELECT id FROM users WHERE email = $1 LIMIT 1',
        [parsed.data.email.toLowerCase()]
      )

      if (rows.length > 0) {
        const token = randomBytes(32).toString('hex')
        const tokenHash = createHash('sha256').update(token).digest('hex')
        const expiresAt = new Date(Date.now() + 60 * 60 * 1000) // 1 hour

        await app.pg.query(
          `INSERT INTO password_reset_tokens (user_id, token_hash, expires_at)
           VALUES ($1, $2, $3)`,
          [rows[0].id, tokenHash, expiresAt]
        )

        await emailService.sendPasswordReset(parsed.data.email, token)
      }
    } catch (err) {
      app.log.error(err, 'forgot-password error')
    }

    return reply.status(200).send({ ok: true })
  })

  // POST /api/auth/reset-password
  app.post('/reset-password', async (request, reply) => {
    const parsed = z.object({
      token: z.string().min(1),
      newPassword: z.string().min(8).max(100),
    }).safeParse(request.body)

    if (!parsed.success) {
      return reply.status(400).send({ error: 'Invalid request' })
    }

    const tokenHash = createHash('sha256').update(parsed.data.token).digest('hex')

    const { rows } = await app.pg.query<{ id: string; user_id: string; expires_at: Date; used_at: Date | null }>(
      `SELECT id, user_id, expires_at, used_at
       FROM password_reset_tokens WHERE token_hash = $1 LIMIT 1`,
      [tokenHash]
    )

    if (rows.length === 0) {
      return reply.status(400).send({ error: 'Invalid or expired reset link' })
    }

    const record = rows[0]

    if (record.used_at) {
      return reply.status(400).send({ error: 'Reset link already used' })
    }

    if (new Date() > record.expires_at) {
      return reply.status(400).send({ error: 'Reset link expired' })
    }

    const passwordHash = await argon2.hash(parsed.data.newPassword)

    await app.pg.query('UPDATE users SET password_hash = $1 WHERE id = $2', [passwordHash, record.user_id])
    await app.pg.query('UPDATE password_reset_tokens SET used_at = NOW() WHERE id = $1', [record.id])

    return reply.status(200).send({ ok: true })
  })

}

// GET /reset-password — HTML page opened in browser from email link
export async function resetPageRoute(app: FastifyInstance) {
  app.get('/reset-password', async (request, reply) => {
    const token = (request.query as any).token ?? ''

    const html = `<!DOCTYPE html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Сброс пароля — Kto-to</title>
  <style>
    *{box-sizing:border-box}
    body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#f5f5f5}
    .card{background:#fff;border-radius:12px;padding:40px 32px;max-width:400px;width:100%;box-shadow:0 2px 16px rgba(0,0,0,.1)}
    h2{margin:0 0 8px;color:#1976d2}
    p{color:#666;margin:0 0 24px;font-size:14px}
    input{width:100%;padding:12px 14px;border:1px solid #ddd;border-radius:8px;font-size:16px;margin-bottom:16px;outline:none}
    input:focus{border-color:#1976d2}
    button{width:100%;padding:13px;background:#1976d2;color:#fff;border:none;border-radius:8px;font-size:16px;cursor:pointer}
    button:hover{background:#1565c0}
    .msg{margin-top:16px;padding:12px;border-radius:8px;font-size:14px;display:none}
    .msg.ok{background:#e8f5e9;color:#2e7d32}
    .msg.err{background:#ffebee;color:#c62828}
  </style>
</head>
<body>
  <div class="card">
    <h2>Kto-to</h2>
    <p>Введите новый пароль</p>
    <input id="pw" type="password" placeholder="Новый пароль (мин. 8 символов)" minlength="8">
    <input id="pw2" type="password" placeholder="Повторите пароль">
    <button onclick="doReset()">Сохранить пароль</button>
    <div id="msg" class="msg"></div>
  </div>
  <script>
    var TOKEN = ${JSON.stringify(token)};
    async function doReset() {
      var pw = document.getElementById('pw').value;
      var pw2 = document.getElementById('pw2').value;
      if (pw.length < 8) return show('Пароль минимум 8 символов', false);
      if (pw !== pw2) return show('Пароли не совпадают', false);
      try {
        var r = await fetch('/api/auth/reset-password', {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({ token: TOKEN, newPassword: pw })
        });
        var data = await r.json();
        if (r.ok) show('Пароль успешно изменён! Войдите в приложение.', true);
        else show(data.error || 'Ошибка', false);
      } catch(e) { show('Нет соединения с сервером', false); }
    }
    function show(text, ok) {
      var el = document.getElementById('msg');
      el.textContent = text;
      el.className = 'msg ' + (ok ? 'ok' : 'err');
      el.style.display = 'block';
    }
  </script>
</body>
</html>`

    return reply.type('text/html').send(html)
  })
}
