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

  const cmds = [
    ['containers', 'docker ps --format "{{.Names}} {{.Status}}"'],
    ['ss inside container', 'docker exec ktoto_backend ss -tlnp 2>&1 || echo "no ss"'],
    ['wget 127.0.0.1', 'docker exec ktoto_backend wget -qO- http://127.0.0.1:3000/health 2>&1; echo "exit:$?"'],
    ['wget localhost', 'docker exec ktoto_backend wget -qO- http://localhost:3000/health 2>&1; echo "exit:$?"'],
    ['curl from host', 'curl -s http://localhost:3000/health 2>&1; echo "exit:$?"'],
    ['container inspect health', 'docker inspect ktoto_backend --format "{{.State.Health.Status}} {{.State.Status}}"'],
    ['last 5 logs', 'docker logs ktoto_backend --tail 5 2>&1'],
  ]

  for (const [name, cmd] of cmds) {
    console.log(`\n=== ${name} ===`)
    const r = await ssh.execCommand(cmd)
    console.log(r.stdout || r.stderr || '(empty)')
  }

  ssh.dispose()
}

main().catch(console.error)
