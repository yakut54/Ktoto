# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Install pnpm
RUN corepack enable && corepack prepare pnpm@9.0.0 --activate

# Copy workspace files
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/admin/package.json ./apps/admin/

# Install only admin deps
RUN pnpm install --filter @ktoto/admin --frozen-lockfile

# Copy source
COPY apps/admin/ ./apps/admin/

# Build
RUN pnpm --filter @ktoto/admin build

# ─── Stage 2: Serve ──────────────────────────────────────────────────────────
FROM nginx:alpine

COPY apps/admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/apps/admin/dist /usr/share/nginx/html

EXPOSE 80
