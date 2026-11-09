// node --test scripts/bump-version.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { nextCode, nextVersion, readVersion, withVersion } from './version-lib.mjs'

test('a patch bump moves the last segment', () => {
  assert.equal(nextVersion('0.3.0'), '0.3.1')
})

test('a patch bump carries into the minor at 100', () => {
  assert.equal(nextVersion('1.9.99'), '1.10.0')
})

test('a short version is padded before it is bumped', () => {
  assert.equal(nextVersion('2'), '2.0.1')
})

test('the next code beats both the published and the local number', () => {
  assert.equal(nextCode(3, 3), 4)
  assert.equal(nextCode(7, 3), 8)
  assert.equal(nextCode(null, 3), 4)
})

test('the version is read from and written back to the gradle file', () => {
  const text = 'versionCode = 4\nversionName = "0.3.1"'
  assert.deepEqual(readVersion(text), { code: 4, name: '0.3.1' })
  assert.match(withVersion(text, 5, '0.3.2'), /versionCode = 5/)
  assert.match(withVersion(text, 5, '0.3.2'), /versionName = "0.3.2"/)
})
