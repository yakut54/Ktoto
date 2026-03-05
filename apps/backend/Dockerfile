# syntax=docker/dockerfile:1

# ---- Build Stage ----
FROM node:20-alpine AS builder

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

WORKDIR /app

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml turbo.json ./
COPY apps/backend/package.json ./apps/backend/

RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
    pnpm install --frozen-lockfile --store-dir /pnpm/store

COPY apps/backend ./apps/backend
RUN pnpm --filter @ktoto/backend build

# ---- Production Stage ----
FROM node:20-alpine AS runner

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

WORKDIR /app

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/backend/package.json ./apps/backend/

RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
    pnpm install --frozen-lockfile --prod --store-dir /pnpm/store

COPY --from=builder /app/apps/backend/dist ./apps/backend/dist
COPY apps/backend/src/db/schema.sql ./apps/backend/dist/db/schema.sql

WORKDIR /app/apps/backend

ENV NODE_ENV=production

EXPOSE 3000

CMD ["node", "dist/server.js"]
