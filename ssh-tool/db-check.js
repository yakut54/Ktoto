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

  // Check backend version (when was image built)
  const ver = await ssh.execCommand('docker exec ktoto_backend node -e "const fs=require(\'fs\');console.log(fs.statSync(\'/app/apps/backend/dist/server.js\').mtime)"')
  console.log('=== Backend build time ===')
  console.log(ver.stdout)

  // Check users in DB
  const users = await ssh.execCommand(
    `docker exec ktoto_db psql -U ktoto -d ktoto -c "SELECT id, username, email, created_at FROM users ORDER BY created_at DESC LIMIT 10;"`
  )
  console.log('=== Users in DB ===')
  console.log(users.stdout || users.stderr)

  ssh.dispose()
}
main().catch(console.error)
