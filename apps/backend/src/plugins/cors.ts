import fp from 'fastify-plugin'
import fastifyCors from '@fastify/cors'

export const corsPlugin = fp(async (app) => {
  await app.register(fastifyCors, {
    origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:5173'],
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  })
})
