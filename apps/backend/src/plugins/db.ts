import fp from 'fastify-plugin'
import fastifyPostgres from '@fastify/postgres'

export const dbPlugin = fp(async (app) => {
  const connectionString =
    process.env.DATABASE_URL ||
    `postgresql://${process.env.DB_USER}:${process.env.DB_PASSWORD}` +
    `@${process.env.DB_HOST}:${process.env.DB_PORT}/${process.env.DB_NAME}`

  await app.register(fastifyPostgres, { connectionString })
  app.log.info('PostgreSQL connected')
})
