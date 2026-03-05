#!/bin/bash
set -e

echo "=== Деплой Mattermost ==="

# 1. Создаём директорию
mkdir -p /var/www/mattermost
cd /var/www/mattermost

# 2. Копируем docker-compose (предполагается что он уже тут)
echo "--- Запуск контейнеров ---"
docker compose up -d

echo "--- Ждём запуска Mattermost (30 сек) ---"
sleep 30

# 3. Проверяем
docker compose ps
echo ""
echo "Mattermost слушает на порту 8065:"
curl -s -o /dev/null -w "%{http_code}" http://localhost:8065

echo ""
echo "=== Готово! Теперь настрой nginx и SSL ==="
echo "Следующий шаг: ./setup_ssl.sh"
