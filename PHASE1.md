# Фаза 1 — Фундамент

## Что сделано
- Монорепо (Turborepo + pnpm workspaces)
- Backend: Fastify + TypeScript + JWT Auth
- PostgreSQL схема (users, conversations, messages, attachments)
- Docker Compose (dev + prod)
- GitHub Actions CI/CD (lint → test → build → deploy)

## Структура
```
apps/backend/src/
├── server.ts          — точка входа
├── app.ts             — сборка Fastify приложения
├── plugins/           — db, redis, cors, jwt
├── routes/auth/       — register, login, refresh, logout, me
├── services/          — AuthService, TokenService
└── db/
    ├── schema.sql     — вся БД схема
    └── migrate.ts     — запуск миграций
```

## API эндпоинты
| Method | Path               | Auth | Описание           |
|--------|--------------------|------|--------------------|
| GET    | /health            | —    | Проверка сервера   |
| GET    | /health/db         | —    | Проверка БД        |
| POST   | /api/auth/register | —    | Регистрация        |
| POST   | /api/auth/login    | —    | Вход               |
| POST   | /api/auth/refresh  | —    | Обновить токен     |
| POST   | /api/auth/logout   | ✓    | Выход              |
| GET    | /api/auth/me       | ✓    | Текущий юзер       |

## Что проверить
1. `cp .env.example .env` — заполнить переменные
2. `cd docker && docker compose -f docker-compose.dev.yml up -d`
3. `pnpm --filter @ktoto/backend migrate`
4. `curl http://localhost:3000/health` → `{"status":"ok"}`
5. POST http://localhost:3000/api/auth/register
   ```json
   {"username":"testuser","email":"test@test.com","password":"password123"}
   ```

## GitHub Secrets (нужно добавить)
В Settings → Secrets and variables → Actions:
- `VPS_HOST` = 31.128.39.216
- `VPS_USER` = root
- `VPS_SSH_KEY` = (приватный ключ SSH)

## Следующая фаза
Фаза 2 — WebSocket + реальная отправка сообщений
