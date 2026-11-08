/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE, as RFC 7636 describes it. A desktop-type OAuth client with a loopback redirect needs this
 * because an installed app cannot keep a secret; the verifier is the secret for one exchange.
 */
object Pkce {

    fun verifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return encode(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return encode(digest)
    }

    fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
