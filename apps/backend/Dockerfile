# ---- Build Stage ----
FROM node:20-alpine AS builder

WORKDIR /app

RUN npm install -g pnpm

COPY package.json pnpm-workspace.yaml turbo.json ./
COPY apps/backend/package.json ./apps/backend/

RUN pnpm install --frozen-lockfile

COPY apps/backend ./apps/backend

RUN pnpm --filter @ktoto/backend build

# ---- Production Stage ----
FROM node:20-alpine AS runner

WORKDIR /app

RUN npm install -g pnpm

COPY package.json pnpm-workspace.yaml ./
COPY apps/backend/package.json ./apps/backend/

RUN pnpm install --prod --frozen-lockfile

COPY --from=builder /app/apps/backend/dist ./apps/backend/dist
COPY --from=builder /app/apps/backend/src/db/schema.sql ./apps/backend/dist/db/schema.sql

WORKDIR /app/apps/backend

ENV NODE_ENV=production

EXPOSE 3000

CMD ["node", "dist/server.js"]
