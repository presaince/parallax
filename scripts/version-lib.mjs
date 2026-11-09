// Pure helpers behind the release version. No I/O here except the manifest read, which the two
// CLIs share; everything else takes and returns strings, so the node:test file can exercise it.

/** "0.3.0" -> "0.3.1"; "1.9.9" -> "1.10.0"; a missing segment counts as 0. */
export function nextVersion(name) {
  const parts = String(name).split('.').map((p) => Number.parseInt(p, 10))
  if (parts.length === 0 || parts.some((p) => Number.isNaN(p) || p < 0)) {
    throw new Error(`not a version: ${name}`)
  }
  while (parts.length < 3) parts.push(0)
  parts[parts.length - 1] += 1
  for (let i = parts.length - 1; i > 0; i--) {
    if (parts[i] > 99) {
      parts[i] = 0
      parts[i - 1] += 1
    }
  }
  return parts.join('.')
}

/**
 * The next versionCode is one above the highest number either side knows about. Using the
 * published manifest as well as the local file means a checked-out tree that was never bumped
 * still cannot publish a code the server already has.
 */
export function nextCode(publishedCode, localCode) {
  return Math.max(publishedCode ?? 0, localCode ?? 0) + 1
}

export function readVersion(gradleText) {
  const code = gradleText.match(/versionCode\s*=\s*(\d+)/)?.[1]
  const name = gradleText.match(/versionName\s*=\s*"([^"]+)"/)?.[1]
  if (!code || !name) throw new Error('no versionCode/versionName in app/build.gradle.kts')
  return { code: Number(code), name }
}

export function withVersion(gradleText, code, name) {
  return gradleText
    .replace(/versionCode\s*=\s*\d+/, `versionCode = ${code}`)
    .replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${name}"`)
}

export function publicBaseFrom(propsText) {
  const base = propsText.match(/^publicBase=(.*)$/m)?.[1]?.trim()
  if (!base) throw new Error('no publicBase in deploy-apk.properties')
  return base.replace(/\/?$/, '/')
}

/**
 * The manifest that is live right now, or null when the server cannot be reached. A release
 * machine with no network cannot publish either, so a null here is a warning, not a stop.
 */
export async function publishedManifest(publicBase, timeoutMs = 15000) {
  const url = `${publicBase}version.json?_=${Date.now()}`
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const r = await fetch(url, { cache: 'no-store', signal: controller.signal })
    if (!r.ok) return null
    return await r.json()
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}
