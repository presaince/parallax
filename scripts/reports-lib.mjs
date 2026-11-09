// Pure helpers behind `scripts/reports.mjs`. Everything here takes and returns strings or plain
// objects, so `reports.test.mjs` can exercise it without a socket or a file.

/** Epoch millis from `parallax_<device>_<code>_yyyyMMdd-HHmmss.json`, or null. */
export function stampFromName(name) {
  const m = String(name).match(/(\d{8})-(\d{6})\.json$/)
  if (!m) return null
  const [_, d, t] = m
  const iso = `${d.slice(0, 4)}-${d.slice(4, 6)}-${d.slice(6, 8)}T${t.slice(0, 2)}:${t.slice(2, 4)}:${t.slice(4, 6)}`
  const at = new Date(iso).getTime()
  return Number.isNaN(at) ? null : at
}

/**
 * The window test. A file whose name carries no readable stamp is out, because guessing would
 * either drop a bug or re-report an old one; `--all` is the escape hatch.
 */
export function withinWindow(stamp, nowMs, hours) {
  if (stamp === null || stamp === undefined) return false
  const age = nowMs - stamp
  return age >= -60_000 && age <= hours * 3_600_000
}

/** One line, no quoting, no newlines, no runaway length: an index line must stay greppable. */
export function tidyNote(note, limit = 160) {
  const flat = String(note ?? '')
    .replace(/\s+/g, ' ')
    .replace(/`/g, "'")
    .trim()
  if (!flat) return '(no note)'
  return flat.length > limit ? `${flat.slice(0, limit - 1)}…` : flat
}

/**
 * What a triage pass needs from one bundle, and nothing else. The log is the reason these files
 * are 40KB; a human (or an agent) only ever wants the lines that did not go to plan.
 */
export function summarise(name, bundle) {
  const entries = Array.isArray(bundle?.entries) ? bundle.entries : []
  const interesting = entries
    .filter((e) => e.level === 'WARN' || e.level === 'ERROR')
    .map((e) => `${e.level} ${e.tag}: ${e.message}`)
  return {
    name,
    at: stampFromName(name),
    version: bundle?.version ?? '?',
    code: bundle?.versionCode ?? '?',
    device: bundle?.device ?? '?',
    note: tidyNote(bundle?.note),
    logsIncluded: bundle?.logsIncluded !== false,
    entries: entries.length,
    interesting: dedupe(interesting),
  }
}

/** A stall repeats every few seconds; the repeat count is the fact, not the repetition. */
export function dedupe(lines) {
  const out = []
  for (const line of lines) {
    const last = out[out.length - 1]
    if (last && last.line === line) last.count += 1
    else out.push({ line, count: 1 })
  }
  return out.map((x) => (x.count > 1 ? `${x.line} (x${x.count})` : x.line))
}

/** The one-line form printed for a batch. Short enough that a whole pull fits on a screen. */
export function brief(s) {
  const logs = s.logsIncluded ? `${s.entries} lines` : 'note only'
  return `${s.name}\n    ${s.version}/${s.code} ${s.device} · ${logs}\n    ${s.note}`
}

/**
 * `- [open] \`file.json\` — the note`. The status sits in a bracket at the start, because a note
 * may contain any punctuation at all and a trailing marker would have to guess where it ends.
 */
const LINE = /^-\s*\[([^\]]*)\]\s*`([^`]+)`\s*—\s*(.*)$/
const OPEN = /^-\s*\[open\]/i

/** Append-only. A line that is already in the index keeps its status: that part was written by hand. */
export function mergeIndex(existingText, summaries) {
  const existing = String(existingText ?? '')
  const known = new Set()
  for (const raw of existing.split(/\r?\n/)) {
    const loose = raw.match(/`([^`]+\.json)`/)
    if (loose) known.add(loose[1])
  }
  const added = summaries
    .filter((s) => !known.has(s.name))
    .sort((a, b) => (a.at ?? 0) - (b.at ?? 0))
    .map((s) => `- [open] \`${s.name}\` — ${s.note}`)
  if (!added.length) return { text: existing, added: 0 }
  const head = existing.replace(/\s*$/, '')
  return { text: `${head}${head ? '\n\n' : ''}${added.join('\n')}\n`, added: added.length }
}

/** `--set "223004=fixed in PlayerScreen.kt"` rewrites the status and never touches the note. */
export function setStatus(text, namePart, status) {
  let hit = 0
  const out = String(text ?? '')
    .split(/\r?\n/)
    .map((line) => {
      const m = line.match(LINE)
      if (!m || !m[2].includes(namePart)) return line
      hit += 1
      return `- [${status.replace(/[[\]]/g, '')}] \`${m[2]}\` — ${m[3]}`
    })
  return { text: out.join('\n'), hit }
}

/** How many reports nobody has answered yet, and which ones. */
export function openLines(text) {
  return String(text ?? '')
    .split(/\r?\n/)
    .filter((line) => OPEN.test(line))
}

export function pendingCount(text) {
  return openLines(text).length
}
