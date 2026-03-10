import nodemailer from 'nodemailer'

export class EmailService {
  private transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST ?? 'smtp.beget.com',
    port: Number(process.env.SMTP_PORT ?? 465),
    secure: true,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  })

  async sendPasswordReset(to: string, token: string): Promise<void> {
    const baseUrl = process.env.API_BASE_URL ?? 'https://api.yakut54.ru'
    const resetUrl = `${baseUrl}/reset-password?token=${token}`

    await this.transporter.sendMail({
      from: `"Kto-to" <${process.env.SMTP_USER}>`,
      to,
      subject: 'Сброс пароля — Kto-to',
      html: `
<!DOCTYPE html>
<html lang="ru">
<head><meta charset="utf-8"></head>
<body style="font-family:sans-serif;max-width:480px;margin:40px auto;padding:0 20px;color:#222">
  <h2 style="color:#1976d2">Kto-to</h2>
  <p>Вы запросили сброс пароля.</p>
  <p>Нажмите кнопку ниже, чтобы задать новый пароль. Ссылка действительна <strong>1 час</strong>.</p>
  <a href="${resetUrl}"
     style="display:inline-block;margin:20px 0;padding:12px 28px;background:#1976d2;color:#fff;text-decoration:none;border-radius:6px;font-size:16px">
    Сбросить пароль
  </a>
  <p style="color:#888;font-size:13px">Если вы не запрашивали сброс — просто проигнорируйте это письмо.</p>
</body>
</html>`,
    })
  }
}
