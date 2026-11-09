// The release version, in one place.
//
//   node scripts/bump-version.mjs            report: local vs published, and whether local is newer
//   node scripts/bump-version.mjs --apply    write versionCode+1 and the next patch into
//                                            app/build.gradle.kts
//
// Why a script: the last release shipped twice with versionCode 3 because the number lived in a
// file a human edited and forgot. The published manifest is read first, so a tree that was never
// bumped still cannot reuse a code the server already serves.
import { readFileSync, writeFileSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { nextCode, nextVersion, publicBaseFrom, publishedManifest, readVersion, withVersion } from './version-lib.mjs'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const gradlePath = join(root, 'app', 'build.gradle.kts')
const propsPath = join(root, 'deploy-apk.properties')
const apply = process.argv.includes('--apply')

const gradleText = readFileSync(gradlePath, 'utf-8')
const local = readVersion(gradleText)
const publicBase = publicBaseFrom(readFileSync(propsPath, 'utf-8'))
const published = await publishedManifest(publicBase)

if (published) {
  console.log(`local      ${local.name} (code ${local.code})`)
  console.log(`published  ${published.versionName} (code ${published.versionCode})`)
} else {
  console.log(`local      ${local.name} (code ${local.code})`)
  console.log(`published  unreadable at ${publicBase}version.json — bumping from the local file only`)
}

const newer = published ? local.code > published.versionCode : true

if (apply) {
  const code = nextCode(published?.versionCode, local.code)
  const name = nextVersion(local.name)
  writeFileSync(gradlePath, withVersion(gradleText, code, name), 'utf-8')
  console.log(`bumped     ${name} (code ${code}) — rebuild and sign, then make-version-json.mjs`)
} else if (!newer) {
  console.log('not newer  this tree would ship a build nobody is offered; run with --apply')
  process.exitCode = 1
} else {
  console.log('newer      ready to build and publish')
}
