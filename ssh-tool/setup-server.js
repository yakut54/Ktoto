const { NodeSSH } = require('node-ssh')
const fs = require('fs')
const path = require('path')

const PRIVATE_KEY = `-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9QAAAJgQgq1uEIKt
bgAAAAtzc2gtZWQyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9Q
AAAEBNVntQer1oYNWc65mwoXLve6gn0PaJ7JX+BSQs+atcsMetAQpyXZr7VjG6THezMZHe
28VQApQhFIsVwKeDCyr1AAAAD3Jvb3RAdWVmend3aXh4cgECAwQFBg==
-----END OPENSSH PRIVATE KEY-----`

async function main() {
  const ssh = new NodeSSH()
  await ssh.connect({ host: '31.128.39.216', username: 'root', privateKey: PRIVATE_KEY })
  console.log('✓ Connected via SSH key')

  // Заливаем docker-compose.prod.yml
  const composeFile = path.resolve('../docker/docker-compose.prod.yml')
  await ssh.putFile(composeFile, '/opt/ktoto/docker/docker-compose.prod.yml')
  console.log('✓ Uploaded docker-compose.prod.yml')

  // Создаём .env.prod если нет
  const envCheck = await ssh.execCommand('test -f /opt/ktoto/docker/.env.prod && echo exists || echo missing')
  console.log('.env.prod:', envCheck.stdout)

  if (envCheck.stdout.trim() === 'missing') {
    const envContent = `NODE_ENV=production
API_PORT=3000
API_HOST=0.0.0.0
DB_HOST=ktoto_db
DB_PORT=5432
DB_NAME=ktoto
DB_USER=ktoto
DB_PASSWORD=Ktoto_Db_Pass_2024!
REDIS_HOST=ktoto_redis
REDIS_PORT=6379
REDIS_PASSWORD=Ktoto_Redis_Pass_2024!
JWT_SECRET=Ktoto_JWT_Secret_Super_Long_32chars!
JWT_ACCESS_EXPIRES=15m
JWT_REFRESH_EXPIRES=7d
CORS_ORIGIN=https://yakut54.ru
GITHUB_REPOSITORY=yakut54/ktoto
IMAGE_TAG=latest`

    await ssh.execCommand(`cat > /opt/ktoto/docker/.env.prod << 'ENVEOF'\n${envContent}\nENVEOF`)
    console.log('✓ Created .env.prod')
  }

  // Запускаем db и redis
  const up = await ssh.execCommand(
    'cd /opt/ktoto/docker && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d db redis 2>&1'
  )
  console.log('DB/Redis:', up.stdout || up.stderr)

  // Проверяем статус
  const ps = await ssh.execCommand('docker ps --format "{{.Names}} {{.Status}}"')
  console.log('\nRunning containers:')
  console.log(ps.stdout)

  ssh.dispose()
  console.log('\n✓ Server setup complete!')
}

main().catch(console.error)
