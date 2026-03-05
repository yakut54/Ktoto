# ---- Build Stage ----
FROM node:20-alpine AS builder

RUN npm install -g pnpm

WORKDIR /app

# Copy workspace manifests + lock file
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml turbo.json ./
COPY apps/backend/package.json ./apps/backend/

# Install all deps
RUN pnpm install --frozen-lockfile

# Copy source and build
COPY apps/backend ./apps/backend
RUN pnpm --filter @ktoto/backend build

# ---- Production Stage ----
FROM node:20-alpine AS runner

RUN npm install -g pnpm

WORKDIR /app

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/backend/package.json ./apps/backend/

RUN pnpm install --frozen-lockfile --prod

COPY --from=builder /app/apps/backend/dist ./apps/backend/dist
COPY apps/backend/src/db/schema.sql ./apps/backend/dist/db/schema.sql

WORKDIR /app/apps/backend

ENV NODE_ENV=production

EXPOSE 3000

CMD ["node", "dist/server.js"]
