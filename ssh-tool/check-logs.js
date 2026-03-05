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

  console.log('=== Container status ===')
  const ps = await ssh.execCommand('docker ps --format "{{.Names}} {{.Status}}"')
  console.log(ps.stdout)

  console.log('\n=== Health check ===')
  const health = await ssh.execCommand('curl -s http://localhost:3000/health')
  console.log(health.stdout || health.stderr)

  console.log('\n=== Backend logs (last 10) ===')
  const logs = await ssh.execCommand('docker logs ktoto_backend --tail 10 2>&1')
  console.log(logs.stdout || logs.stderr)

  ssh.dispose()
}

main().catch(console.error)
