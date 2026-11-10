/**
 * A small FTP client on one kept-alive control connection.
 *
 * Why it exists: `curl ftp://…` opens a fresh TCP connection for every file, and this host starts
 * refusing new connections after a handful in a row — the report pull died with
 * `curl: (7) ... connection refused` while FileZilla and `FtpWebRequest` were fine. One session,
 * login, CWD once, then NLST and RETR over it, is what the working clients do.
 *
 * Only what the report pull needs: login, list, download, quit. Passive mode only, binary only.
 */
import { connect } from 'node:net'
import { once } from 'node:events'

const CRLF = '\r\n'

/** `227 Entering Passive Mode (10,0,0,5,195,80)` -> `{ host: '10.0.0.5', port: 50000 }` */
export function parsePasv(text) {
  const m = String(text).match(/(\d{1,3}),(\d{1,3}),(\d{1,3}),(\d{1,3}),(\d{1,3}),(\d{1,3})/)
  if (!m) return null
  const n = m.slice(1).map(Number)
  if (n.some((v) => v > 255)) return null
  return { host: n.slice(0, 4).join('.'), port: n[4] * 256 + n[5] }
}

/**
 * A server behind NAT answers PASV with its own private address, which a client on the internet
 * cannot reach. Anything unroutable is replaced by the address the control connection came in on.
 */
export function usableHost(pasvHost, controlHost) {
  const priv =
    /^(0|10|127)\./.test(pasvHost) ||
    /^192\.168\./.test(pasvHost) ||
    /^169\.254\./.test(pasvHost) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(pasvHost)
  return priv ? controlHost : pasvHost
}

function linePump(sock) {
  let buffer = ''
  const queue = []
  let failure = null
  let wake = null
  const notify = () => {
    if (wake) {
      const w = wake
      wake = null
      w()
    }
  }
  sock.setEncoding('latin1')
  sock.on('data', (chunk) => {
    buffer += chunk
    let i
    while ((i = buffer.indexOf('\n')) >= 0) {
      queue.push(buffer.slice(0, i).replace(/\r$/, ''))
      buffer = buffer.slice(i + 1)
    }
    notify()
  })
  const stop = (error) => {
    failure = failure ?? error
    notify()
  }
  sock.on('error', stop)
  sock.on('close', () => stop(new Error('ftp: connection closed')))
  return async () => {
    for (;;) {
      if (queue.length) return queue.shift()
      if (failure) throw failure
      await new Promise((resolve) => {
        wake = resolve
      })
    }
  }
}

/** One FTP reply: `150-…` continuation lines up to the matching `150 ` line, kept whole. */
async function readReply(next) {
  const first = await next()
  const code = Number(first.slice(0, 3))
  const lines = [first]
  if (first[3] === '-') {
    for (;;) {
      const line = await next()
      lines.push(line)
      if (line.startsWith(`${code} `)) break
    }
  }
  return { code, text: lines.join('\n') }
}

export async function openSession({ host, port = 21, user, password, timeout = 20000 }) {
  const control = connect({ host, port })
  control.setTimeout(timeout, () => control.destroy(new Error(`ftp: ${host}:${port} timed out`)))
  await once(control, 'connect')
  const next = linePump(control)

  const ask = async (command) => {
    control.write(`${command}${CRLF}`)
    return readReply(next)
  }
  const expect = async (command, ok, what) => {
    const r = await ask(command)
    if (!ok.includes(r.code)) throw new Error(`ftp ${what}: ${r.text.replace(/\n/g, ' ')}`)
    return r
  }

  const greeting = await readReply(next)
  if (greeting.code !== 220) throw new Error(`ftp greeting: ${greeting.text}`)

  const loggedIn = await ask(`USER ${user}`)
  if (loggedIn.code === 331) {
    const passed = await ask(`PASS ${password}`)
    if (passed.code !== 230) throw new Error(`ftp login: ${passed.text.replace(/\n/g, ' ')}`)
  } else if (loggedIn.code !== 230) {
    throw new Error(`ftp login: ${loggedIn.text.replace(/\n/g, ' ')}`)
  }
  await expect('TYPE I', [200], 'binary mode')

  async function passive() {
    const r = await ask('PASV')
    if (r.code !== 227) throw new Error(`ftp passive: ${r.text.replace(/\n/g, ' ')}`)
    const p = parsePasv(r.text)
    if (!p) throw new Error(`ftp passive: no address in "${r.text}"`)
    const data = connect({ host: usableHost(p.host, host), port: p.port })
    data.setTimeout(timeout, () => data.destroy(new Error('ftp: data connection timed out')))
    await once(data, 'connect')
    return data
  }

  function collect(data) {
    return new Promise((resolve, reject) => {
      const chunks = []
      data.on('data', (c) => chunks.push(c))
      data.on('end', () => resolve(Buffer.concat(chunks)))
      data.on('error', reject)
      data.resume()
    })
  }

  async function transfer(command, what) {
    const data = await passive()
    const bytes = collect(data)
    const started = await ask(command)
    if (started.code !== 150 && started.code !== 125) {
      data.destroy()
      throw new Error(`ftp ${what}: ${started.text.replace(/\n/g, ' ')}`)
    }
    const body = await bytes
    const done = await readReply(next)
    if (done.code !== 226 && done.code !== 250) {
      throw new Error(`ftp ${what}: ${done.text.replace(/\n/g, ' ')}`)
    }
    return body
  }

  return {
    async listNames(dir) {
      await expect(`CWD ${dir}`, [250], `cwd ${dir}`)
      const body = await transfer('NLST', `list ${dir}`)
      return body
        .toString('latin1')
        .split(/\r?\n/)
        .map((s) => s.trim())
        .filter(Boolean)
    },
    /** `name` is relative to the directory passed to `listNames`. */
    download: (name) => transfer(`RETR ${name}`, `retr ${name}`),
    async close() {
      try {
        await ask('QUIT')
      } catch {
        // the server hanging up first is a normal end, not a failure
      }
      control.destroy()
    },
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** This host drops new connections in bursts, so a refused connect is retried, not reported. */
export async function openSessionWithRetry(options, attempts = 4, delayMs = 2000) {
  let last
  for (let n = 1; n <= attempts; n++) {
    try {
      return await openSession(options)
    } catch (error) {
      last = error
      if (n < attempts) await sleep(delayMs)
    }
  }
  throw last
}
