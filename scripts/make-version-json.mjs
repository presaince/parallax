// Writes version.json next to the signed release APK, for the self-updater. Run after apksigner:
//
//   node scripts/make-version-json.mjs --notes "What changed, one line per row"
//
// versionCode/versionName come from the APK's output-metadata.json — the APK is the source of
// truth, the file in build.gradle.kts may lag behind it. Must run AFTER signing, because the
// sha256 is of the signed bytes. The URL is read from deploy-apk.properties so the client
// verifies against the same origin the file actually lands at. The deploy config defers
// version.json, so the APK goes up first and the manifest second.
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const args = process.argv.slice(2)
const notes = args[args.indexOf('--notes') + 1]?.startsWith('--') ? '' : (args[args.indexOf('--notes') + 1] ?? '')

const releaseDir = join(root, 'app', 'build', 'outputs', 'apk', 'release')
const apkPath = join(releaseDir, 'app-release.apk')
const metaPath = join(releaseDir, 'output-metadata.json')
if (!existsSync(apkPath)) throw new Error(`missing ${apkPath} — build and sign first`)
if (!existsSync(metaPath)) throw new Error(`missing ${metaPath} — build first`)

const meta = JSON.parse(readFileSync(metaPath, 'utf-8'))
const element = meta.elements?.[0]
const versionCode = element?.versionCode
const versionName = element?.versionName
if (versionCode == null || !versionName) throw new Error('no versionCode/versionName in output-metadata.json')

const props = readFileSync(join(root, 'deploy-apk.properties'), 'utf-8')
const publicBase = props.match(/^publicBase=(.*)$/m)?.[1]?.trim().replace(/\/?$/, '/') ?? ''
if (!publicBase) throw new Error('no publicBase in deploy-apk.properties')

const sha256 = createHash('sha256').update(readFileSync(apkPath)).digest('hex')
const versionJson = JSON.stringify(
  {
    versionCode,
    versionName,
    apkUrl: `${publicBase}app-release.apk`,
    sha256,
    notes: { en: notes },
  },
  null,
  2,
)
writeFileSync(join(releaseDir, 'version.json'), versionJson + '\n', 'utf-8')
console.log(`version.json written: versionCode=${versionCode} versionName=${versionName} sha256=${sha256.slice(0, 12)}…`)
