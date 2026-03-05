const { NodeSSH } = require('node-ssh')

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

  // Test login with current backend
  const test = await ssh.execCommand(
    `curl -s -X POST http://localhost:3000/api/auth/login -H "Content-Type: application/json" -d '{"username":"yakut54","password":"12345678"}'`
  )
  console.log('=== Login test (username) ===')
  console.log(test.stdout)

  // Pull latest image and redeploy
  console.log('\n=== Pulling latest image ===')
  const pull = await ssh.execCommand(
    `cd /opt/ktoto/docker && docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend 2>&1 | tail -5`
  )
  console.log(pull.stdout)

  const up = await ssh.execCommand(
    `cd /opt/ktoto/docker && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend 2>&1 | tail -5`
  )
  console.log(up.stdout)

  ssh.dispose()
}
main().catch(console.error)
