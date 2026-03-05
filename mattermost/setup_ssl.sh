#!/bin/bash
set -e

DOMAIN="chat.yakut54.ru"
EMAIL="ftm.marta.ng@gmail.com"

echo "=== Настройка SSL для $DOMAIN ==="

# 1. Проверяем наличие nginx на хосте
if ! command -v nginx &> /dev/null; then
    echo "--- Устанавливаем nginx ---"
    apt update && apt install -y nginx
fi

# 2. Устанавливаем certbot
if ! command -v certbot &> /dev/null; then
    echo "--- Устанавливаем certbot ---"
    apt install -y certbot python3-certbot-nginx
fi

# 3. Копируем конфиг nginx
echo "--- Настраиваем nginx конфиг ---"
cp /var/www/mattermost/nginx.conf /etc/nginx/sites-available/chat.yakut54.ru
ln -sf /etc/nginx/sites-available/chat.yakut54.ru /etc/nginx/sites-enabled/chat.yakut54.ru

# 4. Временно nginx без SSL (для certbot challenge)
# Certbot сам пропишет SSL после получения сертификата
nginx -t && systemctl reload nginx

# 5. Получаем SSL сертификат
echo "--- Получаем SSL сертификат ---"
certbot --nginx -d $DOMAIN --email $EMAIL --agree-tos --non-interactive

# 6. Автообновление сертификата
echo "--- Настраиваем автообновление ---"
(crontab -l 2>/dev/null; echo "0 3 * * * certbot renew --quiet && systemctl reload nginx") | crontab -

echo ""
echo "=== SSL настроен! ==="
echo "Открывай: https://$DOMAIN"
