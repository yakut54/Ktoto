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

  // Create admin user for backend (publisher)
  const add = await ssh.execCommand(
    'docker exec ktoto_ntfy ntfy user add --role=admin --password="ntfy-backend-2026" ktoto-backend 2>&1'
  )
  console.log('Add user:', add.stdout || add.stderr)

  // Create subscriber user (for Android app)
  const addSub = await ssh.execCommand(
    'docker exec ktoto_ntfy ntfy user add --role=user --password="ntfy-client-2026" ktoto-client 2>&1'
  )
  console.log('Add subscriber:', addSub.stdout || addSub.stderr)

  // Allow subscriber to read all ktoto-* topics
  const access = await ssh.execCommand(
    'docker exec ktoto_ntfy ntfy access ktoto-client "ktoto-*" read 2>&1'
  )
  console.log('Access:', access.stdout || access.stderr)

  // Test publish
  const test = await ssh.execCommand(
    'curl -s -u ktoto-backend:ntfy-backend-2026 -d "test" http://localhost:2586/ktoto-test'
  )
  console.log('Test publish:', test.stdout)

  // Test subscribe (SSE, just check it connects)
  const sub = await ssh.execCommand(
    'curl -s -u ktoto-client:ntfy-client-2026 "http://localhost:2586/ktoto-test/json?poll=1"'
  )
  console.log('Test subscribe:', sub.stdout)

  ssh.dispose()
}
main().catch(console.error)
