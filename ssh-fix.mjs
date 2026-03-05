import { NodeSSH } from 'node-ssh'

const ssh = new NodeSSH()

await ssh.connect({
  host: '31.128.39.216',
  username: 'root',
  password: 'GbfCn&Cw+D86',
})

console.log('Connected!')

// Читаем приватный ключ
const key = await ssh.execCommand('cat ~/.ssh/ktoto')
console.log('=== PRIVATE KEY ===')
console.log(key.stdout)

// Читаем pub ключ для проверки
const pub = await ssh.execCommand('cat ~/.ssh/ktoto.pub')
console.log('=== PUBLIC KEY ===')
console.log(pub.stdout)

// Проверяем authorized_keys
const auth = await ssh.execCommand('cat ~/.ssh/authorized_keys')
console.log('=== AUTHORIZED KEYS ===')
console.log(auth.stdout)

ssh.dispose()
