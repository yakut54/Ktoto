const { NodeSSH } = require('node-ssh')
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

  // Upload updated docker-compose
  await ssh.putFile(
    path.join(__dirname, '../docker/docker-compose.prod.yml'),
    '/opt/ktoto/docker/docker-compose.prod.yml'
  )
  console.log('Uploaded docker-compose.prod.yml')

  // Start ntfy
  const up = await ssh.execCommand(
    'cd /opt/ktoto/docker && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d ntfy 2>&1'
  )
  console.log(up.stdout || up.stderr)

  // Wait for ntfy to start
  await new Promise(r => setTimeout(r, 3000))

  // Create backend publisher user (can publish to any topic)
  const addPublisher = await ssh.execCommand(
    'docker exec ktoto_ntfy ntfy user add --role=admin ktoto-backend 2>&1 || true'
  )
  console.log('Add publisher:', addPublisher.stdout || addPublisher.stderr)

  // Set password for backend user
  const setPass = await ssh.execCommand(
    'docker exec ktoto_ntfy ntfy user change-pass ktoto-backend <<< "ktoto-ntfy-secret-2026\nktoto-ntfy-secret-2026" 2>&1 || true'
  )
  console.log('Set pass:', setPass.stdout || setPass.stderr)

  // Check health
  const health = await ssh.execCommand('curl -s http://localhost:2586/v1/health')
  console.log('ntfy health:', health.stdout)

  ssh.dispose()
}
main().catch(console.error)
