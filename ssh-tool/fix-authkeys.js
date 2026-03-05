const { NodeSSH } = require('node-ssh')
const ssh = new NodeSSH()

async function main() {
  await ssh.connect({
    host: '31.128.39.216',
    username: 'root',
    password: 'GbfCn&Cw+D86',
  })
  console.log('Connected via password')

  // Читаем текущий authorized_keys
  const current = await ssh.execCommand('cat ~/.ssh/authorized_keys')
  console.log('Current authorized_keys:')
  console.log(JSON.stringify(current.stdout))

  // Парсим строки и чиним
  const lines = current.stdout
    .split('\n')
    .flatMap(line => {
      // Разбиваем строки где ssh- встречается в середине
      return line.split(/(ssh-ed25519 |ssh-rsa )/).reduce((acc, part, i, arr) => {
        if (part === 'ssh-ed25519 ' || part === 'ssh-rsa ') {
          acc.push(part + arr[i + 1])
        } else if (i > 0 && (arr[i - 1] === 'ssh-ed25519 ' || arr[i - 1] === 'ssh-rsa ')) {
          // уже добавили
        } else if (part.trim()) {
          acc.push(part.trim())
        }
        return acc
      }, [])
    })
    .filter(l => l.startsWith('ssh-'))

  console.log('\nParsed keys:')
  lines.forEach((l, i) => console.log(`  ${i + 1}: ${l.substring(0, 50)}...`))

  // Записываем обратно с правильными переносами
  const fixed = lines.join('\n') + '\n'
  await ssh.execCommand(`echo '${fixed.replace(/'/g, "'\\''")}' > ~/.ssh/authorized_keys`)

  // Проверяем результат
  const check = await ssh.execCommand('cat ~/.ssh/authorized_keys')
  console.log('\nFixed authorized_keys:')
  console.log(check.stdout)

  // Тестируем ключ
  ssh.dispose()

  // Подключаемся по ключу
  const PRIVATE_KEY = `-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9QAAAJgQgq1uEIKt
bgAAAAtzc2gtZWQyNTUxOQAAACDHrQEKcl2a+1Yxukx3szGR3tvFUAKUIRSLFcCngwsq9Q
AAAEBNVntQer1oYNWc65mwoXLve6gn0PaJ7JX+BSQs+atcsMetAQpyXZr7VjG6THezMZHe
28VQApQhFIsVwKeDCyr1AAAAD3Jvb3RAdWVmend3aXh4cgECAwQFBg==
-----END OPENSSH PRIVATE KEY-----`

  const ssh2 = new NodeSSH()
  await ssh2.connect({
    host: '31.128.39.216',
    username: 'root',
    privateKey: PRIVATE_KEY,
  })
  console.log('\n✓ SSH key auth works now!')

  // Создаём структуру для деплоя
  const setup = await ssh2.execCommand('mkdir -p /opt/ktoto/docker && echo "dirs created"')
  console.log(setup.stdout)

  ssh2.dispose()
}

main().catch(console.error)
