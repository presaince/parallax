#!/usr/bin/env node
/**
 * Bug reports in, a short list out.
 *
 *   node scripts/reports.mjs                 pull the last 3 hours, print one block per report
 *   node scripts/reports.mjs --hours=24      a wider window
 *   node scripts/reports.mjs --all           every report on the server
 *   node scripts/reports.mjs --open          only what the index still calls open
 *   node scripts/reports.mjs --log=223004    one report's warn/error lines
 *   node scripts/reports.mjs --set "223004=fixed in PlayerScreen.kt"   mark it done
 *   node scripts/reports.mjs --refresh       re-download files already on disk
 *
 * Why it exists: each bundle is up to 40KB, mostly HTTP chatter, and reading twelve of them to
 * find five sentences is the most expensive part of fixing a batch. The default output is a note
 * and a version per report; the log needs a flag naming one report. `docs/reports.md` is the
 * memory: append-only, one line per report, a status that survives every later pull.
 *
 * Credentials come from ~/.ftp/accounts.properties through the project's own deploy config. They
 * are never printed, and never written anywhere.
 *
 * The pull runs over one FTP session (`./ftp-lib.mjs`): login once, CWD once, then one RETR per
 * file. A connection per file is what the old curl version did, and this host refuses new
 * connections after a handful in a row — refusal was read as "server down" when it was not.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { homedir } from 'node:os'
import { openSessionWithRetry } from './ftp-lib.mjs'
import {
  brief,
  mergeIndex,
  openLines,
  pendingCount,
  setStatus,
  stampFromName,
  summarise,
  withinWindow,
} from './reports-lib.mjs'

const ROOT = resolve(import.meta.dirname, '..')
const CACHE = join(ROOT, '.opencode', 'scratch', 'ftp-reports')
const INDEX = join(ROOT, 'docs', 'reports.md')
const HEADER = `# Bug reports

One line per report the app filed, newest last. \`open\` means nothing has changed yet; anything
else is what happened. Written by \`node scripts/reports.mjs\`, which never rewrites a line that
already has a status.

`

function args() {
  const out = { hours: 3, all: false, open: false, log: null, set: null, refresh: false }
  const argv = process.argv.slice(2)
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--all') out.all = true
    else if (a === '--open') out.open = true
    else if (a === '--refresh') out.refresh = true
    else if (a === '--help' || a === '-h') out.help = true
    else if (a.startsWith('--hours=')) out.hours = Number(a.slice(8))
    else if (a.startsWith('--log=')) out.log = a.slice(6)
    else if (a.startsWith('--set=')) out.set = a.slice(6)
    else if (a === '--log') out.log = argv[++i] ?? null
    else if (a === '--set') out.set = argv[++i] ?? null
    else if (a.startsWith('--hours')) out.hours = Number(argv[++i] ?? 3)
    else throw new Error(`unknown flag ${a}`)
  }
  return out
}

function props(file) {
  const map = {}
  if (!existsSync(file)) return map
  for (const raw of readFileSync(file, 'utf-8').split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || line.startsWith('#')) continue
    const i = line.indexOf('=')
    if (i > 0) map[line.slice(0, i).trim()] = line.slice(i + 1).trim()
  }
  return map
}

function connection() {
  const cfg = props(join(ROOT, 'deploy-reports.properties'))
  if (!cfg.account || !cfg.remoteDir) {
    throw new Error('deploy-reports.properties needs account= and remoteDir=')
  }
  const accounts = props(join(homedir(), '.ftp', 'accounts.properties'))
  const host = accounts[`${cfg.account}.host`]
  const user = accounts[`${cfg.account}.user`]
  const password = accounts[`${cfg.account}.password`]
  if (!host || !user || !password) throw new Error(`no ${cfg.account} credentials in ~/.ftp`)
  // upload.php writes beside the app's folder, one level up from remoteDir.
  const reportsDir = `${cfg.remoteDir.replace(/\/+$/, '').split('/').slice(0, -1).join('/')}/parallax_reports`
  return { host, user, password, reportsDir }
}

function remoteNames(session, conn) {
  return session
    .listNames(conn.reportsDir)
    .then((names) => names.filter((n) => n.endsWith('.json')))
}

async function fetch(session, name, refresh) {
  const local = join(CACHE, name)
  if (!refresh && existsSync(local) && statSync(local).size > 0) return local
  const body = await session.download(name)
  if (!body.length) throw new Error(`empty download: ${name}`)
  mkdirSync(CACHE, { recursive: true })
  writeFileSync(local, body)
  return local
}

function readIndex() {
  return existsSync(INDEX) ? readFileSync(INDEX, 'utf-8') : HEADER
}

function writeIndex(text) {
  mkdirSync(join(ROOT, 'docs'), { recursive: true })
  writeFileSync(INDEX, text.endsWith('\n') ? text : `${text}\n`, 'utf-8')
}

async function main() {
  const opt = args()
  if (opt.help) {
    console.log(readFileSync(import.meta.filename, 'utf-8').split('*/')[0].replace(/^#!.*\n/, ''))
    return
  }

  if (opt.set) {
    const i = opt.set.indexOf('=')
    if (i < 1) throw new Error('--set needs filename-fragment=status')
    const { text, hit } = setStatus(readIndex(), opt.set.slice(0, i), opt.set.slice(i + 1))
    if (!hit) throw new Error(`no index line matched ${opt.set.slice(0, i)}`)
    writeIndex(text)
    console.log(`marked ${hit} line(s); ${pendingCount(text)} still open`)
    return
  }

  if (opt.open) {
    const lines = openLines(readIndex())
    console.log(lines.length ? lines.join('\n') : 'nothing open')
    console.log(`\n${lines.length} open · index ${INDEX}`)
    return
  }

  const conn = connection()
  const now = Date.now()
  const session = await openSessionWithRetry({
    host: conn.host,
    user: conn.user,
    password: conn.password,
  })
  try {
    const names = await remoteNames(session, conn)
    const wanted = opt.all
      ? names
      : names.filter((n) => withinWindow(stampFromName(n), now, opt.hours))

    if (!wanted.length) {
      console.log(`no reports in the last ${opt.hours}h (${names.length} on the server)`)
      return
    }

    const summaries = []
    for (const name of wanted.sort()) {
      const local = await fetch(session, name, opt.refresh)
      let bundle = null
      try {
        bundle = JSON.parse(readFileSync(local, 'utf-8'))
      } catch {
        console.log(`${name}\n    unreadable JSON, kept at ${local}`)
        continue
      }
      summaries.push(summarise(name, bundle))
    }

    if (opt.log) {
      const hits = summaries.filter((s) => s.name.includes(opt.log))
      if (!hits.length) throw new Error(`no report matched ${opt.log}`)
      for (const s of hits) {
        console.log(`${s.name}  ${s.version}/${s.code}  ${s.note}`)
        if (!s.interesting.length) console.log('    (nothing above info)')
        for (const line of s.interesting) console.log(`    ${line}`)
      }
      return
    }

    for (const s of summaries) console.log(brief(s))

    const { text, added } = mergeIndex(readIndex(), summaries)
    if (added) writeIndex(text)
    else if (!existsSync(INDEX)) writeIndex(text)
    console.log(
      `\n${summaries.length} report(s), ${added} new in the index · ${pendingCount(text)} open · ${INDEX}`,
    )
  } finally {
    await session.close()
  }
}

main().catch((error) => {
  console.error(`reports failed: ${error.message}`)
  process.exit(1)
})
