import 'dotenv/config'
import pg from 'pg'
import { readFileSync } from 'fs'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

const __dirname = dirname(fileURLToPath(import.meta.url))

const client = new pg.Client({
  host: process.env.DB_HOST || 'localhost',
  port: Number(process.env.DB_PORT) || 5432,
  database: process.env.DB_NAME || 'ktoto',
  user: process.env.DB_USER || 'ktoto',
  password: process.env.DB_PASSWORD,
})

async function migrate() {
  await client.connect()
  console.log('Connected to PostgreSQL')
  console.log('Running migrations...')

  const schemaPath = join(__dirname, 'schema.sql')
  const sql = readFileSync(schemaPath, 'utf-8')
  await client.query(sql)

  console.log('✓ Migrations complete')
  await client.end()
}

migrate().catch((err) => {
  console.error('✗ Migration failed:', err.message)
  process.exit(1)
})
