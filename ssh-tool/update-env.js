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

  const r = await ssh.execCommand('cat /opt/ktoto/docker/.env.prod')
  const env = r.stdout

  if (!env.includes('NTFY_URL')) {
    await ssh.execCommand(`cat >> /opt/ktoto/docker/.env.prod << 'ENVEOF'

# ntfy push notifications
NTFY_URL=http://ktoto_ntfy:80
NTFY_USER=ktoto-backend
NTFY_PASS=ntfy-backend-2026
ENVEOF`)
    console.log('Added NTFY vars to .env.prod')
  } else {
    console.log('NTFY vars already present')
  }

  const check = await ssh.execCommand('grep NTFY /opt/ktoto/docker/.env.prod')
  console.log(check.stdout)

  ssh.dispose()
}
main().catch(console.error)
