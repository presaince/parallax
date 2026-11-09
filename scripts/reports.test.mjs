// node --test scripts/reports.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  brief,
  dedupe,
  mergeIndex,
  openLines,
  pendingCount,
  setStatus,
  stampFromName,
  summarise,
  tidyNote,
  withinWindow,
} from './reports-lib.mjs'

const NOW = new Date('2026-11-09T21:00:00Z').getTime()

test('a report filename carries its own timestamp', () => {
  // The app names a report with the phone's wall clock, so the parser reads it the same way.
  assert.equal(
    stampFromName('parallax_Google_Pixel_7_10_20261109-210000.json'),
    new Date(2026, 10, 9, 21, 0, 0).getTime(),
  )
  assert.equal(stampFromName('parallax_test_0_notatime.json'), null)
})

test('the window keeps the last three hours and nothing older', () => {
  assert.equal(withinWindow(NOW - 60 * 60 * 1000, NOW, 3), true)
  assert.equal(withinWindow(NOW - 4 * 60 * 60 * 1000, NOW, 3), false)
  assert.equal(withinWindow(null, NOW, 3), false, 'an unreadable stamp is not a guess')
})

test('a note becomes one greppable line', () => {
  assert.equal(tidyNote('  buffering\n just   happened '), 'buffering just happened')
  assert.equal(tidyNote(''), '(no note)')
  assert.equal(tidyNote(`uses \`ticks\``), "uses 'ticks'")
  assert.equal(tidyNote('x'.repeat(300)).length, 160)
})

test('only the lines that did not go to plan survive triage', () => {
  const bundle = {
    version: '0.3.7',
    versionCode: 10,
    device: 'Google Pixel 7',
    note: 'buffering',
    entries: [
      { level: 'INFO', tag: 'http', message: 'POST next -> 200' },
      { level: 'WARN', tag: 'player', message: 'stalled 15093ms' },
      { level: 'WARN', tag: 'player', message: 'stalled 15093ms' },
      { level: 'ERROR', tag: 'source', message: 'streams failed' },
    ],
  }
  const s = summarise('a_20261109-210000.json', bundle)
  assert.equal(s.note, 'buffering')
  assert.equal(s.entries, 4)
  assert.deepEqual(s.interesting, [
    'WARN player: stalled 15093ms (x2)',
    'ERROR source: streams failed',
  ])
  assert.match(brief(s), /0\.3\.7\/10 Google Pixel 7 · 4 lines/)
})

test('a note-only report says so instead of showing an empty log', () => {
  const s = summarise('a_20261109-210000.json', { version: '0.3.7', logsIncluded: false })
  assert.match(brief(s), /note only/)
  assert.deepEqual(s.interesting, [])
})

test('new reports append, and a written status is never overwritten', () => {
  const before = '- [fixed in `PlayerScreen.kt`] `a.json` — first thing\n'
  const { text, added } = mergeIndex(before, [
    { name: 'a.json', at: 1, note: 'first thing' },
    { name: 'b.json', at: 2, note: 'second thing' },
  ])
  assert.equal(added, 1)
  assert.match(text, /fixed in `PlayerScreen\.kt`/)
  assert.match(text, /- \[open\] `b\.json` — second thing/)
})

test('a second pull of the same batch adds nothing', () => {
  const once = mergeIndex('', [{ name: 'a.json', at: 1, note: 'thing' }]).text
  const twice = mergeIndex(once, [{ name: 'a.json', at: 1, note: 'thing' }])
  assert.equal(twice.added, 0)
  assert.equal(twice.text, once)
})

test('the header and the list stay one blank line apart', () => {
  const { text } = mergeIndex('# Bug reports\n\nIntro.\n', [{ name: 'a.json', at: 1, note: 'x' }])
  assert.equal(text, '# Bug reports\n\nIntro.\n\n- [open] `a.json` — x\n')
})

test('a status is set by filename fragment and the open count follows', () => {
  const text = '- [open] `a_223004.json` — blast — open\n- [open] `b_223137.json` — rotate\n'
  const { text: after, hit } = setStatus(text, '223004', 'fixed in PlayerScreen.kt, 2026-11-09')
  assert.equal(hit, 1)
  assert.match(after, /- \[fixed in PlayerScreen\.kt, 2026-11-09\] `a_223004\.json` — blast — open/)
  assert.doesNotMatch(after, /\[fixed[^\]]*\] `b_223137/)
  assert.match(after, /- \[open\] `b_223137\.json` — rotate/)
  assert.equal(pendingCount(after), 1)
})

test('dedupe counts repeats rather than printing them', () => {
  assert.deepEqual(dedupe(['a', 'a', 'a', 'b']), ['a (x3)', 'b'])
})

test('the open list is a set of whole lines, not a suffix guess', () => {
  const text = [
    '# Report',
    '- [open] `a.json` — note that ends with the word open',
    '- [fixed 2026-11-09] `b.json` — done',
    '- [open] `c.json` — another',
  ].join('\n')
  assert.equal(pendingCount(text), 2)
  assert.equal(openLines(text).length, 2)
  assert.match(openLines(text)[0], /a\.json/)
})
