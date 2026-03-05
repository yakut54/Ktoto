import 'dotenv/config'
import { buildApp } from './app.js'

const start = async () => {
  const app = await buildApp()

  const port = Number(process.env.API_PORT) || 3000
  const host = process.env.API_HOST || '0.0.0.0'

  try {
    await app.listen({ port, host })
    app.log.info(`Server running at http://${host}:${port}`)
  } catch (err) {
    app.log.error(err)
    process.exit(1)
  }
}

start()
