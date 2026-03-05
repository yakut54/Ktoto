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

  await ssh.putFile(
    path.join(__dirname, '../docker/docker-compose.prod.yml'),
    '/opt/ktoto/docker/docker-compose.prod.yml'
  )
  console.log('Uploaded docker-compose.prod.yml')

  const r = await ssh.execCommand(
    'cd /opt/ktoto/docker && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend'
  )
  console.log(r.stdout || r.stderr)

  const status = await ssh.execCommand('docker ps --format "{{.Names}} {{.Status}}"')
  console.log(status.stdout)

  ssh.dispose()
}

main().catch(console.error)
