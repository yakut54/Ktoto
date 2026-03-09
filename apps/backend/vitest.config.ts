import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    testTimeout: 30_000,
    hookTimeout: 30_000,
    teardownTimeout: 30_000,
    // singleFork: all test files run sequentially in one process.
    // Required because tests share a single postgres DB — concurrent forks
    // would race on truncateAll() and corrupt each other's data.
    pool: 'forks',
    poolOptions: {
      forks: { singleFork: true },
    },
    sequence: { concurrent: false },
    globalSetup: ['./src/__tests__/helpers/globalSetup.ts'],
    env: {
      NODE_ENV: 'test',
      DB_HOST: process.env.DB_HOST ?? 'localhost',
      DB_PORT: process.env.DB_PORT ?? '5432',
      DB_NAME: process.env.DB_NAME ?? 'ktoto_test',
      DB_USER: process.env.DB_USER ?? 'ktoto',
      DB_PASSWORD: process.env.DB_PASSWORD ?? 'test_password',
      REDIS_HOST: process.env.REDIS_HOST ?? 'localhost',
      REDIS_PORT: process.env.REDIS_PORT ?? '6379',
      JWT_SECRET: 'test_secret_at_least_32_characters_long',
      MINIO_ENDPOINT: 'http://localhost:19999', // no real MinIO — mocked in tests
    },
  },
})
