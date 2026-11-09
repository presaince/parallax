/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The update path can install an APK, so every predicate that gates it is tested here. These are
 * pure functions on purpose: the network half is exercised by hand, the decisions are not.
 */
class UpdateManagerTest {

    @Test
    fun `a cached archive is reused only when its digest is the published one`() {
        val file = File.createTempFile("app-update", ".apk")
        try {
            file.writeBytes(ByteArray(1_100_000) { (it % 251).toByte() })
            val sha = UpdateManager.fileSha256(file)
            assertTrue(UpdateManager.cacheReusable(file, sha))
            assertFalse(UpdateManager.cacheReusable(file, "0".repeat(64)))
            assertFalse(UpdateManager.cacheReusable(file, null))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an archive too small to be an APK is never reused`() {
        val file = File.createTempFile("app-update", ".apk")
        try {
            file.writeBytes(ByteArray(1024))
            val sha = UpdateManager.fileSha256(file)
            assertFalse(UpdateManager.cacheReusable(file, sha))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a missing cached archive is not reused`() {
        val missing = File("does-not-exist-${System.nanoTime()}.apk")
        assertFalse(UpdateManager.cacheReusable(missing, "0".repeat(64)))
    }

    @Test
    fun `versionNameGreater compares patch bumps`() {
        assertTrue(UpdateManager.versionNameGreater("0.3.9", "0.3.8"))
        assertFalse(UpdateManager.versionNameGreater("0.3.8", "0.3.9"))
        assertFalse(UpdateManager.versionNameGreater("0.3.8", "0.3.8"))
    }

    @Test
    fun `versionNameGreater compares across segments`() {
        assertTrue(UpdateManager.versionNameGreater("0.4.0", "0.3.99"))
        assertTrue(UpdateManager.versionNameGreater("1.0.0", "0.9.9"))
        assertFalse(UpdateManager.versionNameGreater("0.10.0", "0.11.0"))
    }

    @Test
    fun `versionNameGreater handles different lengths`() {
        assertTrue(UpdateManager.versionNameGreater("1.2.3", "1.2"))
        assertFalse(UpdateManager.versionNameGreater("1.2", "1.2.3"))
    }

    @Test
    fun `versionNameGreater handles unparseable input`() {
        assertFalse(UpdateManager.versionNameGreater("beta", "0.3.8"))
        assertFalse(UpdateManager.versionNameGreater("", ""))
        // An unparseable installed version has no segments, so any numeric candidate is newer.
        assertTrue(UpdateManager.versionNameGreater("0.3.8", "beta"))
    }

    @Test
    fun `isTrustedApkUrl accepts only the configured https origin`() {
        assertTrue(
            UpdateManager.isTrustedApkUrl(
                "https://odesaplay.com.ua/other_apps/parallax/app-release.apk",
            ),
        )
        assertTrue(
            UpdateManager.isTrustedApkUrl(
                "https://ODESAPLAY.COM.UA/other_apps/parallax/app-release.apk",
            ),
        )

        // A tampered version.json must not get to choose where the APK comes from, including
        // suffix and userinfo look-alikes.
        assertFalse(UpdateManager.isTrustedApkUrl("https://evil.example/app-release.apk"))
        assertFalse(
            UpdateManager.isTrustedApkUrl(
                "https://odesaplay.com.ua.evil.example/app-release.apk",
            ),
        )
        assertFalse(
            UpdateManager.isTrustedApkUrl(
                "https://odesaplay.com.ua@evil.example/app-release.apk",
            ),
        )

        // Scheme and port are part of the origin.
        assertFalse(
            UpdateManager.isTrustedApkUrl("http://odesaplay.com.ua/app-release.apk"),
        )
        assertFalse(
            UpdateManager.isTrustedApkUrl("https://odesaplay.com.ua:8443/app-release.apk"),
        )
        assertFalse(UpdateManager.isTrustedApkUrl("not a url"))
    }

    @Test
    fun `sha256Matches requires an exact digest`() {
        assertTrue(UpdateManager.sha256Matches(" ABC123 ", "abc123"))
        assertFalse(UpdateManager.sha256Matches("abc123", "abc124"))
    }

    @Test
    fun `signersMatch requires readable, identical signer sets`() {
        val signers = setOf("aa", "bb")
        assertTrue(UpdateManager.signersMatch(signers, setOf("aa", "bb")))
        assertFalse(UpdateManager.signersMatch(signers, setOf("aa")))
        assertFalse(UpdateManager.signersMatch(signers, setOf("aa", "cc")))
        // Unreadable signers are disqualifying, never a free pass.
        assertFalse(UpdateManager.signersMatch(null, signers))
        assertFalse(UpdateManager.signersMatch(signers, null))
        assertFalse(UpdateManager.signersMatch(emptySet(), emptySet()))
    }

    @Test
    fun `installable refuses an older archive in every mode`() {
        // The forced path still never installs a downgrade: the installer would reject it, and the
        // version in that rejection is the only thing that names the desync out loud.
        assertFalse(UpdateManager.installable(790, 791, allowSameVersion = false))
        assertFalse(UpdateManager.installable(790, 791, allowSameVersion = true))
        assertFalse(UpdateManager.installable(1, 790, allowSameVersion = true))
    }

    @Test
    fun `installable admits a newer archive and, when forced, a rebuild of the same one`() {
        assertTrue(UpdateManager.installable(792, 791, allowSameVersion = false))
        // Same version: refused by the tap path, a repair by the forced one.
        assertFalse(UpdateManager.installable(791, 791, allowSameVersion = false))
        assertTrue(UpdateManager.installable(791, 791, allowSameVersion = true))
    }

    @Test
    fun `cacheBusted keeps the trusted origin and defeats a cached copy`() {
        val url = "https://odesaplay.com.ua/other_apps/parallax/app-release.apk"
        val busted = UpdateManager.cacheBusted(url, 1_700_000_000_000L)

        assertTrue(busted.startsWith("$url?_="))
        assertTrue(UpdateManager.isTrustedApkUrl(busted))
        // Same origin, different URL: the origin still serves the same file, but nothing cached
        // under the old key can answer it.
        assertFalse(busted == url)
    }

    @Test
    fun `cacheBusted appends to an existing query`() {
        val url = "https://odesaplay.com.ua/other_apps/parallax/app-release.apk?v=2"
        assertTrue(UpdateManager.cacheBusted(url, 42L).startsWith("$url&_=42"))
    }
}
