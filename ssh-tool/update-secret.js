const { NodeSSH } = require('node-ssh')
const ssh = new NodeSSH()

// Полный приватный ключ с сервера
const PRIVATE_KEY = `-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9QAAAJgQgq1uEIKt
bgAAAAtzc2gtZWQyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9Q
AAAEBNVntQer1oYNWc65mwoXLve6gn0PaJ7JX+BSQs+atcsMetAQpyXZr7VjG6THezMZHe
28VQApQhFIsVwKeDCyr1AAAAD3Jvb3RAdWVmend3aXh4cgECAwQFBg==
-----END OPENSSH PRIVATE KEY-----`

async function main() {
  // Проверяем что ключ работает
  await ssh.connect({
    host: '31.128.39.216',
    username: 'root',
    privateKey: PRIVATE_KEY,
  })

  console.log('✓ SSH key works!')

  // Создаём структуру на сервере
  const cmds = [
    'mkdir -p /opt/ktoto/docker',
    'echo "SSH key auth confirmed"',
  ]

  for (const cmd of cmds) {
    const r = await ssh.execCommand(cmd)
    if (r.stdout) console.log(r.stdout)
  }

  ssh.dispose()
}

main().catch(console.error)
