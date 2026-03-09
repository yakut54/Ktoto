import pg from 'pg'
import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))

export async function setup() {
  const client = new pg.Client({
    host: process.env.DB_HOST ?? 'localhost',
    port: Number(process.env.DB_PORT ?? 5432),
    database: process.env.DB_NAME ?? 'ktoto_test',
    user: process.env.DB_USER ?? 'ktoto',
    password: process.env.DB_PASSWORD ?? 'test_password',
  })

  try {
    await client.connect()
    const schema = readFileSync(join(__dirname, '../../db/schema.sql'), 'utf-8')
    await client.query(schema)
    console.log('[globalSetup] Schema applied to ktoto_test')
  } catch (err) {
    console.error('[globalSetup] FAILED — is PostgreSQL running?', (err as Error).message)
    throw err
  } finally {
    await client.end()
  }
}

export async function teardown() {
  // nothing — containers managed externally
}
