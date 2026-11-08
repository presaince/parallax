/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.settings

import androidx.annotation.DrawableRes
import com.presaince.parallax.R

/**
 * Settings is a list of pages, not one long scroll. Every control lives on exactly one page, and
 * search resolves to both the page and the row, so a typo still lands somewhere useful.
 */
enum class SettingsSectionId {
    APPEARANCE,
    PLAYBACK,
    FEEDBACK,
    REGION,
    SPONSOR,
    ACCOUNTS,
    DATA,
    DIAGNOSTICS,
    ABOUT,
}

val SettingsSectionId.title: String
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> "Appearance"
        SettingsSectionId.PLAYBACK -> "Playback"
        SettingsSectionId.FEEDBACK -> "Feedback"
        SettingsSectionId.REGION -> "Content region"
        SettingsSectionId.SPONSOR -> "SponsorBlock"
        SettingsSectionId.ACCOUNTS -> "AI & accounts"
        SettingsSectionId.DATA -> "Data"
        SettingsSectionId.DIAGNOSTICS -> "Diagnostics"
        SettingsSectionId.ABOUT -> "About"
    }

@get:DrawableRes
val SettingsSectionId.icon: Int
    get() = when (this) {
        SettingsSectionId.APPEARANCE -> R.drawable.ic_palette
        SettingsSectionId.PLAYBACK -> R.drawable.ic_speed
        SettingsSectionId.FEEDBACK -> R.drawable.ic_volume
        SettingsSectionId.REGION -> R.drawable.ic_public
        SettingsSectionId.SPONSOR -> R.drawable.ic_sponsor
        SettingsSectionId.ACCOUNTS -> R.drawable.ic_resync
        SettingsSectionId.DATA -> R.drawable.ic_data
        SettingsSectionId.DIAGNOSTICS -> R.drawable.ic_logs
        SettingsSectionId.ABOUT -> R.drawable.ic_info
    }

/** One searchable row. The keywords are the words a person types, not the words on screen. */
data class SettingsEntry(
    val title: String,
    val section: SettingsSectionId,
    val keywords: List<String>,
)

private fun entry(title: String, section: SettingsSectionId, vararg keywords: String) =
    SettingsEntry(title, section, keywords.map { it.trim() }.filter { it.isNotEmpty() })

val settingsEntries: List<SettingsEntry> = listOf(
    entry("Theme", SettingsSectionId.APPEARANCE,
        "theme", "themes", "appearance", "look", "style", "mode", "dark", "light", "system",
        "auto", "night", "day", "black", "white", "glass", "glassmorphism", "frosted",
        "transparent", "translucent", "glow", "follow system", "colour scheme", "color scheme"),
    entry("Accent", SettingsSectionId.APPEARANCE,
        "accent", "colour", "color", "hue", "palette", "tint", "shade", "swatch", "pink", "red",
        "orange", "amber", "yellow", "lime", "green", "emerald", "teal", "cyan", "aqua", "sky",
        "blue", "indigo", "violet", "purple", "magenta"),
    entry("Shuffle daily", SettingsSectionId.APPEARANCE,
        "shuffle", "daily", "every day", "each day", "random accent", "rotate", "surprise",
        "changing", "changes", "automatic accent", "new colour"),
    entry("Solid surfaces", SettingsSectionId.APPEARANCE,
        "solid", "surfaces", "surface", "opaque", "transparency", "translucent", "glass",
        "contrast", "readable", "readability", "blur", "flat", "performance"),
    entry("Preview", SettingsSectionId.APPEARANCE,
        "preview", "example", "sample", "demo", "look", "theme", "accent"),
    entry("Video cards", SettingsSectionId.APPEARANCE,
        "card", "cards", "row", "rows", "title", "channel name", "ago", "age", "views",
        "view count", "likes", "duration", "length", "metadata", "details"),
    entry("Haptics", SettingsSectionId.FEEDBACK,
        "haptics", "haptic", "vibration", "vibrate", "touch", "feedback", "buzz", "tick", "ticks",
        "cancel", "snap", "seek cancel"),
    entry("Seek cancel snap", SettingsSectionId.FEEDBACK,
        "seek", "cancel", "snap", "home", "zero", "don't seek", "dont seek", "undo seek"),
    entry("Speed home snap", SettingsSectionId.FEEDBACK,
        "speed", "snap", "home", "1.0", "normal speed", "cancel"),
    entry("Volume and brightness clamps", SettingsSectionId.FEEDBACK,
        "volume", "brightness", "clamp", "end", "max", "min", "limit", "snap"),
    entry("Chapter milestones", SettingsSectionId.FEEDBACK,
        "chapter", "chapters", "milestone", "milestones", "boundary", "boundaries", "tick"),
    entry("Gestures", SettingsSectionId.PLAYBACK,
        "gesture", "gestures", "tap", "swipe", "slide", "seek", "scrub", "volume", "brightness",
        "minimise", "minimize", "flick", "double tap", "tutorial", "help", "controls"),
    entry("Swipe to seek", SettingsSectionId.PLAYBACK,
        "seek", "seeking", "swipe", "scrub", "sensitivity", "sensitive", "precision", "speed",
        "seconds", "gentle", "normal", "quick", "fast", "slow", "scrub speed", "skip"),
    entry("Speed presets", SettingsSectionId.PLAYBACK,
        "speed", "speeds", "rate", "playback speed", "player", "fast", "faster", "slow",
        "slower", "slow motion", "half speed", "0.5", "0.8", "1.0", "1.2", "1.5", "2.0", "2x",
        "3x", "4x", "5x", "0.1 steps"),
    entry("Hide live and upcoming", SettingsSectionId.PLAYBACK,
        "live", "upcoming", "premiere", "premieres", "stream", "streams", "feed", "search",
        "channel", "hide", "filter", "live stream", "scheduled"),
    entry("Progress line in fullscreen", SettingsSectionId.PLAYBACK,
        "progress", "line", "bar", "progress bar", "fullscreen", "full screen", "scrub",
        "timeline", "landscape", "bottom"),
    entry("Video quality", SettingsSectionId.PLAYBACK,
        "quality", "resolution", "video quality", "1080", "1080p", "720", "720p", "480", "480p",
        "360", "360p", "240", "240p", "auto", "hd", "data saver", "bitrate", "pixels"),
    entry("Restart button in the notification", SettingsSectionId.PLAYBACK,
        "restart", "notification", "shade", "media controls", "media button", "previous",
        "back to start", "lock screen"),
    entry("Content region", SettingsSectionId.REGION,
        "country", "region", "youtube country", "locale", "location", "language", "content",
        "trending", "recommendations"),
    entry("Pop out when you leave the app", SettingsSectionId.PLAYBACK,
        "picture in picture", "picture", "pip", "pop out", "popout", "pop up", "floating",
        "float", "window", "leave", "background", "home button", "mini"),
    entry("Skip SponsorBlock segments", SettingsSectionId.SPONSOR,
        "sponsorblock", "sponsor", "sponsors", "segment", "segments", "skip", "ad", "ads",
        "advert", "adverts", "advertising"),
    entry("SponsorBlock categories", SettingsSectionId.SPONSOR,
        "intro", "outro", "filler", "self promotion", "self promo", "interaction", "music",
        "non music", "preview", "recap", "category", "categories", "sponsorblock", "tea",
        "highlight", "tangent"),
    entry("AI mode", SettingsSectionId.ACCOUNTS,
        "ai", "ai mode", "resync", "re sync", "timestamp", "timestamps", "align", "alignment", "comment",
        "comments", "offset", "clock", "cue", "subtitle", "subtitles", "timing", "chapters", "summary"),
    entry("Model endpoint (AI)", SettingsSectionId.ACCOUNTS,
        "endpoint", "url", "server", "model", "api", "api key", "key", "token", "llm", "ai",
        "openai", "ollama", "local model", "prompt", "gemini", "anthropic"),
    entry("Post comments", SettingsSectionId.ACCOUNTS,
        "post", "posting", "comment", "comments", "reply", "replies", "account", "google",
        "oauth", "client id", "client secret", "sign in", "sign out", "login", "logout",
        "youtube data api", "credentials"),
    entry("Import subscriptions", SettingsSectionId.DATA,
        "import", "importing", "subscriptions", "subscription", "subs", "channels", "newpipe",
        "libretube", "libre tube", "takeout", "google takeout", "csv", "json", "zip", "backup",
        "restore", "migrate", "migration"),
    entry("Export subscriptions", SettingsSectionId.DATA,
        "export", "exporting", "subscriptions", "subscription", "subs", "channels", "backup",
        "json", "file", "save", "share", "copy"),
    entry("Export settings", SettingsSectionId.DATA,
        "export", "settings", "preferences", "backup", "json", "file", "theme", "accent",
        "sponsorblock", "search history"),
    entry("Import a Bored recipe", SettingsSectionId.DATA,
        "recipe", "recipes", "bored", "bored mode", "import", "json", "discover", "preset",
        "custom"),
    entry("Self repair", SettingsSectionId.DIAGNOSTICS,
        "repair", "self repair", "selfrepair", "canary", "extractor", "youtube", "repair log",
        "summary", "explain", "how does this work", "diagnostics", "hx"),
    entry("Logs", SettingsSectionId.DIAGNOSTICS,
        "log", "logs", "logging", "diagnostics", "error", "errors", "history", "debug",
        "crash", "crashes", "problem", "trouble", "support"),
    entry("About Parallax", SettingsSectionId.ABOUT,
        "about", "version", "licence", "license", "gpl", "gplv3", "credits", "app", "source",
        "code", "github", "copyright", "build"),
)

/** Lowercase, no quotes, dashes as spaces: the shape a query is compared in. */
internal fun searchNorm(text: String): String = text.lowercase()
    .replace(Regex("['\u2019\u00B4`]"), "")
    .replace(Regex("[-_/\u2013\u2014]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

/**
 * Settings search. Every query word has to hit something: an exact word, a prefix, a substring,
 * or a near miss of one letter (or one swapped pair, which is the common typing error).
 */
fun searchSettings(query: String, entries: List<SettingsEntry> = settingsEntries): List<SettingsEntry> {
    val words = searchNorm(query).split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return entries
        .mapNotNull { entry -> entryScore(words, entry)?.let { score -> score to entry } }
        .sortedWith(compareBy({ it.first }, { it.second.title }))
        .map { it.second }
}

private fun entryScore(words: List<String>, entry: SettingsEntry): Int? {
    var total = 0
    for (word in words) {
        val score = wordScore(word, entry) ?: return null
        total += score
    }
    return total
}

private fun wordScore(word: String, entry: SettingsEntry): Int? {
    var best: Int? = null
    fun consider(token: String) {
        val score = when {
            token == word -> 0
            token.startsWith(word) -> 1
            token.contains(word) -> 2
            nearMiss(word, token) -> 3
            else -> return
        }
        if (best == null || score < best!!) best = score
    }
    tokens(entry.title).forEach(::consider)
    tokens(entry.section.title).forEach(::consider)
    entry.keywords.forEach { keyword -> tokens(keyword).forEach(::consider) }
    return best
}

private fun tokens(text: String): List<String> =
    searchNorm(text).split(" ").filter { it.isNotBlank() }

/** One edit is tolerated from four letters up: "sponsr", "apearance", "commnets". */
private fun nearMiss(word: String, token: String): Boolean {
    if (word.length < 4 || token.length < 4) return false
    if (kotlin.math.abs(word.length - token.length) > 1) return false
    return distance(word, token) <= 1
}

/** Damerau-Levenshtein, restricted to the adjacent-swap case so "commnets" costs one. */
internal fun distance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(
                d[i - 1][j] + 1,
                d[i][j - 1] + 1,
                d[i - 1][j - 1] + cost,
            )
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
    }
    return d[a.length][b.length]
}
