const { NodeSSH } = require('node-ssh')
const ssh = new NodeSSH()

async function main() {
  await ssh.connect({
    host: '31.128.39.216',
    username: 'root',
    password: 'GbfCn&Cw+D86',
  })

  console.log('Connected!')

  const key = await ssh.execCommand('cat ~/.ssh/ktoto')
  console.log(key.stdout)

  ssh.dispose()
}

main().catch(console.error)
