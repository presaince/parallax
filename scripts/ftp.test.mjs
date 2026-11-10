// node --test scripts/ftp.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:net'
import { parsePasv, openSession, usableHost } from './ftp-lib.mjs'

/** A fake ProFTPD: just enough commands for a pull, and it counts logins. */
function fakeFtp(files, { multiline = false } = {}) {
  const state = { connections: 0, commands: [] }
  const server = createServer((socket) => {
    state.connections++
    socket.setEncoding('latin1')
    socket.write(multiline ? '220-Welcome\r\n220 ready\r\n' : '220 ready\r\n')
    let buffer = ''
    let dataServer = null
    let dataSocket = null
    const ok = (line) => socket.write(`${line}\r\n`)
    socket.on('data', (chunk) => {
      buffer += chunk
      let i
      while ((i = buffer.indexOf('\r\n')) >= 0) {
        const line = buffer.slice(0, i)
        buffer = buffer.slice(i + 2)
        const [verb, ...rest] = line.split(' ')
        const arg = rest.join(' ')
        state.commands.push(verb)
        if (verb === 'USER') ok('331 password please')
        else if (verb === 'PASS') ok('230 logged in')
        else if (verb === 'TYPE') ok('200 binary')
        else if (verb === 'CWD') ok('250 directory changed')
        else if (verb === 'PASV') {
          dataServer = createServer((d) => {
            dataSocket = d
          })
          dataServer.listen(0, '127.0.0.1', () => {
            const port = dataServer.address().port
            ok(`227 Entering Passive Mode (10,1,2,3,${Math.floor(port / 256)},${port % 256})`)
          })
        } else if (verb === 'NLST' || verb === 'RETR') {
          const payload =
            verb === 'NLST'
              ? Object.keys(files)
                  .map((name) => `${name}\r\n`)
                  .join('')
              : files[arg]
          ok('150 opening data connection')
          dataSocket.end(payload, () => {
            ok('226 transfer complete')
            dataServer.close()
          })
        } else if (verb === 'QUIT') {
          ok('221 bye')
          socket.end()
        } else ok('500 unknown')
      }
    })
  })
  return { state, server }
}

test('a PASV reply carries the host and port a client needs', () => {
  assert.deepEqual(parsePasv('227 Entering Passive Mode (185,70,111,250,195,80).'), {
    host: '185.70.111.250',
    port: 50000,
  })
  assert.equal(parsePasv('227 Entering Passive Mode'), null)
})

test('an unroutable PASV address is replaced by the control connection host', () => {
  // A server behind NAT answers with its own 10.x/192.168.x address, which is unreachable from here.
  assert.equal(usableHost('10.1.2.3', '185.70.111.250'), '185.70.111.250')
  assert.equal(usableHost('192.168.0.9', '185.70.111.250'), '185.70.111.250')
  assert.equal(usableHost('172.20.1.1', '185.70.111.250'), '185.70.111.250')
  assert.equal(usableHost('185.70.111.250', '185.70.111.250'), '185.70.111.250')
})

test('a whole pull is one login, not one per file', async () => {
  // The bug this replaces: curl opened a connection per file and the host started refusing them.
  const { state, server } = fakeFtp({ 'a.json': '{"note":"one"}', 'b.json': '{"note":"two"}' })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address()
  const session = await openSession({ host: '127.0.0.1', port, user: 'u', password: 'p' })
  try {
    assert.deepEqual(await session.listNames('reports'), ['a.json', 'b.json'])
    assert.equal((await session.download('a.json')).toString(), '{"note":"one"}')
    assert.equal((await session.download('b.json')).toString(), '{"note":"two"}')
  } finally {
    await session.close()
    server.close()
  }
  assert.equal(state.connections, 1)
  assert.equal(state.commands.filter((c) => c === 'USER').length, 1)
})

test('a multi-line greeting is read as one reply', async () => {
  const { server } = fakeFtp({}, { multiline: true })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address()
  const session = await openSession({ host: '127.0.0.1', port, user: 'u', password: 'p' })
  try {
    assert.deepEqual(await session.listNames('reports'), [])
  } finally {
    await session.close()
    server.close()
  }
})
