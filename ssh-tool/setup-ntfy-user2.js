const { NodeSSH } = require('node-ssh')

const PRIVATE_KEY = `-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9QAAAJgQgq1uEIKt
bgAAAAtzc2gtZWQyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9Q
AAAEBNVntQer1oYNWc65mwoXLve6gn0PaJ7JX+BSQs+atcsMetAQpyXZr7VjG6THezMZHe
28VQApQhFIsVwKeDCyr1AAAAD3Jvb3RAdWVmend3aXh4cgECAwQFBg==
-----END OPENSSH PRIVATE KEY-----`

async function run(ssh, cmd) {
  const r = await ssh.execCommand(cmd)
  console.log(r.stdout || r.stderr)
  return r
}

async function main() {
  const ssh = new NodeSSH()
  await ssh.connect({ host: '31.128.39.216', username: 'root', privateKey: PRIVATE_KEY })

  // Backend admin user
  await run(ssh, 'docker exec -e NTFY_PASSWORD=ntfy-backend-2026 ktoto_ntfy ntfy user add --role=admin --ignore-exists ktoto-backend')

  // Client user (Android app)
  await run(ssh, 'docker exec -e NTFY_PASSWORD=ntfy-client-2026 ktoto_ntfy ntfy user add --role=user --ignore-exists ktoto-client')

  // Allow client to read ktoto-* topics
  await run(ssh, 'docker exec ktoto_ntfy ntfy access ktoto-client "ktoto-*" read')

  // Test publish
  await run(ssh, 'curl -s -u ktoto-backend:ntfy-backend-2026 -d "hello from backend" http://localhost:2586/ktoto-test')

  // Test receive
  await run(ssh, 'curl -s -u ktoto-client:ntfy-client-2026 "http://localhost:2586/ktoto-test/json?poll=1"')

  ssh.dispose()
}
main().catch(console.error)
