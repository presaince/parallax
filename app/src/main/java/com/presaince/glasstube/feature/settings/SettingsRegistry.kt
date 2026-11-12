/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.settings

import androidx.annotation.DrawableRes
import com.presaince.glasstube.R

/**
 * Settings is a list of pages, not one long scroll. Every control lives on exactly one page, and
 * search resolves to both the page and the row, so a typo still lands somewhere useful.
 */
enum class SettingsSectionId {
    APPEARANCE,
    PLAYBACK,
    SOUND,
    FEED,
    HAPTICS,
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
        SettingsSectionId.SOUND -> "Sound"
        SettingsSectionId.FEED -> "Feed"
        SettingsSectionId.HAPTICS -> "Haptics"
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
        SettingsSectionId.SOUND -> R.drawable.ic_equalizer
        SettingsSectionId.FEED -> R.drawable.ic_history
        SettingsSectionId.HAPTICS -> R.drawable.ic_volume
        SettingsSectionId.REGION -> R.drawable.ic_public
        SettingsSectionId.SPONSOR -> R.drawable.ic_sponsor
        SettingsSectionId.ACCOUNTS -> R.drawable.ic_resync
        SettingsSectionId.DATA -> R.drawable.ic_data
        SettingsSectionId.DIAGNOSTICS -> R.drawable.ic_logs
        SettingsSectionId.ABOUT -> R.drawable.ic_info
    }

/**
 * One searchable row. The title is the words on screen, so the result can be recognised; the
 * keywords are the words a person types instead.
 *
 * [runs] marks a result that starts something rather than opening a page: Run setup again launches
 * the wizard from the index, so a search result has to do the same instead of landing on About.
 */
data class SettingsEntry(
    val title: String,
    val section: SettingsSectionId,
    val keywords: List<String>,
    val runs: Boolean = false,
)

private fun entry(
    title: String,
    section: SettingsSectionId,
    vararg keywords: String,
    runs: Boolean = false,
) = SettingsEntry(
    title,
    section,
    keywords.map { it.trim() }.filter { it.isNotEmpty() },
    runs,
)

val settingsEntries: List<SettingsEntry> = listOf(
    // Appearance: how anything looks, anywhere. The player's chrome and the video page's layout
    // are here because they are the same question as the theme, not a question about playing.
    entry("Theme", SettingsSectionId.APPEARANCE,
        "theme", "themes", "appearance", "look", "style", "mode", "dark", "light", "system",
        "auto", "night", "day", "black", "white", "glass", "glassmorphism", "frosted",
        "transparent", "translucent", "glow", "follow system", "colour scheme", "color scheme"),
    entry("Accent", SettingsSectionId.APPEARANCE,
        "accent", "colour", "color", "hue", "palette", "tint", "shade", "swatch", "pink", "red",
        "orange", "amber", "yellow", "lime", "green", "emerald", "teal", "cyan", "aqua", "sky",
        "blue", "indigo", "violet", "purple", "magenta", "system colour"),
    entry("Shuffle", SettingsSectionId.APPEARANCE,
        "shuffle", "daily", "hourly", "focus", "every day", "each day", "random accent", "rotate",
        "surprise", "changing", "changes", "automatic accent", "new colour", "open"),
    entry("Preview", SettingsSectionId.APPEARANCE,
        "preview", "example", "sample", "demo", "video cards", "card", "cards", "layout", "side",
        "classic", "minimal", "title", "channel name", "ago", "views", "likes", "duration",
        "watch count", "comments", "progress", "notes", "row", "rows"),
    entry("Contrast", SettingsSectionId.APPEARANCE,
        "contrast", "readable", "readability", "legible", "wcag", "aa", "aaa", "ratio",
        "hard to read", "faint", "dim", "accessibility", "a11y", "text contrast", "brightness",
        "low brightness"),
    entry("Animations", SettingsSectionId.APPEARANCE,
        "animations", "animation", "motion", "movement", "still", "sweep", "turn off motion"),
    entry("While a video loads", SettingsSectionId.APPEARANCE,
        "loading", "loading style", "kinetic", "still", "random", "none", "wait", "thumbnail",
        "spinner", "first frame"),
    entry("Progress bar in fullscreen", SettingsSectionId.APPEARANCE,
        "progress", "line", "bar", "progress bar", "fullscreen", "full screen", "scrub",
        "timeline", "landscape", "bottom", "hide controls"),
    entry("Restart button in the notification", SettingsSectionId.APPEARANCE,
        "restart", "notification", "shade", "media controls", "media button", "previous",
        "back to start", "lock screen"),
    entry("Tint from video", SettingsSectionId.APPEARANCE,
        "tint", "tint from video", "video tint", "dominant colour", "colour from video",
        "player colour", "theme from video", "advanced"),
    entry("Chapters", SettingsSectionId.APPEARANCE,
        "chapters", "chapter", "row", "list", "hidden", "chapter panel", "chapter strip",
        "chapter cards"),
    entry("Video page order", SettingsSectionId.APPEARANCE,
        "order", "sections", "video page", "page layout", "drag", "arrange", "reorder",
        "description", "comments", "up next", "my notes", "top to bottom"),
    entry("Chapter timestamps", SettingsSectionId.APPEARANCE,
        "chapter timestamps", "timestamps", "chapter times", "chapter titles", "time"),
    entry("Section labels", SettingsSectionId.APPEARANCE,
        "labels", "section labels", "right", "left", "heading", "header", "description",
        "chapters", "comments", "up next"),

    // Playback: how playing works. Nothing here changes what a page looks like.
    entry("Gestures", SettingsSectionId.PLAYBACK,
        "gesture", "gestures", "tap", "swipe", "slide", "seek", "scrub", "volume", "brightness",
        "minimise", "minimize", "flick", "double tap", "tutorial", "help", "controls", "lesson"),
    entry("Swipe to seek", SettingsSectionId.PLAYBACK,
        "seek", "seeking", "swipe", "scrub", "sensitivity", "sensitive", "precision", "speed",
        "seconds", "gentle", "normal", "quick", "scrub speed", "skip", "base"),
    entry("Speeds in the slider", SettingsSectionId.PLAYBACK,
        "speed", "speeds", "rate", "playback speed", "player", "fast", "faster", "slow",
        "slower", "slow motion", "half speed", "presets", "0.5", "0.8", "1.0", "1.2", "1.5",
        "2.0", "2x", "3x", "4x", "5x", "0.1 steps"),
    entry("Video quality", SettingsSectionId.PLAYBACK,
        "quality", "resolution", "video quality", "144", "144p", "240", "240p", "360", "360p",
        "480", "480p", "720", "720p", "1080", "1080p", "auto", "hd", "data saver", "bitrate",
        "pixels", "wifi", "wi fi", "wireless", "mobile data", "cellular", "4g", "5g", "metered"),
    entry("Stream cache", SettingsSectionId.PLAYBACK,
        "cache", "stream cache", "disk", "bytes", "offline", "reopen", "buffer", "1gb", "2gb",
        "4gb", "storage", "watched bytes"),
    entry("Pop out when you leave the app", SettingsSectionId.PLAYBACK,
        "picture in picture", "picture", "pip", "pop out", "popout", "pop up", "floating",
        "float", "window", "leave", "background", "home button", "mini"),
    entry("Show captions", SettingsSectionId.PLAYBACK,
        "captions", "caption", "subtitles", "subtitle", "cc", "closed captions", "sub", "subs",
        "transcript", "words", "text", "hearing", "deaf", "accessibility", "language", "english"),
    entry("Caption size", SettingsSectionId.PLAYBACK,
        "caption", "captions", "subtitle", "subtitles", "size", "text size", "small", "medium",
        "large", "big", "readable", "legibility", "font"),
    entry("Equaliser and level", SettingsSectionId.SOUND,
        "sound", "audio", "eq", "equaliser", "equalizer", "bass", "treble", "voice", "shape",
        "level", "volume", "boost", "louder", "quiet", "gain", "loudness", "headphones"),
    entry("Even out volume", SettingsSectionId.SOUND,
        "even out", "even", "normalise", "normalize", "loudness", "stabilizer", "stabiliser",
        "compressor", "compression", "quiet", "loud", "ads", "levels", "dynamic range"),

    // Feed: what the lists and the end of a video offer. Live, watched, related, Bored.
    entry("Hide live and upcoming", SettingsSectionId.FEED,
        "live", "upcoming", "premiere", "premieres", "stream", "streams", "feed", "search",
        "channel", "hide", "filter", "live stream", "scheduled"),
    entry("Ask channels for live streams", SettingsSectionId.FEED,
        "live", "streams", "live tab", "requests", "refresh", "fetch", "channels", "feed",
        "data", "half the requests"),
    entry("Count a watch after", SettingsSectionId.FEED,
        "watch", "watched", "watch history", "history", "count", "threshold", "30 seconds",
        "minute", "percent", "when is it watched"),
    entry("Hide watched", SettingsSectionId.FEED,
        "watched", "hide watched", "feed", "filter", "progress", "percent", "history",
        "leave the feed", "finished"),
    entry("Related at the end", SettingsSectionId.FEED,
        "related", "up next", "recommendations", "end screen", "end", "next video"),
    entry("Skip subscribed channels", SettingsSectionId.FEED,
        "bored", "skip", "subscribed", "subscriptions", "channels", "discover", "walk"),

    entry("Haptics", SettingsSectionId.HAPTICS,
        "haptics", "haptic", "vibration", "vibrate", "touch", "feedback", "buzz", "tick", "ticks",
        "test tick"),
    entry("Seek cancel snap", SettingsSectionId.HAPTICS,
        "seek", "cancel", "snap", "home", "zero", "don't seek", "dont seek", "undo seek"),
    entry("Speed home snap", SettingsSectionId.HAPTICS,
        "speed", "snap", "home", "1.0", "normal speed", "cancel"),
    entry("Volume and brightness ends", SettingsSectionId.HAPTICS,
        "volume", "brightness", "ends", "clamp", "max", "min", "limit", "snap", "top", "bottom"),
    entry("Chapter milestones", SettingsSectionId.HAPTICS,
        "chapter", "chapters", "milestone", "milestones", "boundary", "boundaries", "tick"),
    entry("Content region", SettingsSectionId.REGION,
        "country", "region", "youtube country", "locale", "location", "language", "content",
        "trending", "recommendations"),
    entry("Skip SponsorBlock", SettingsSectionId.SPONSOR,
        "sponsorblock", "sponsor", "sponsors", "segment", "segments", "skip", "ad", "ads",
        "advert", "adverts", "advertising"),
    entry("SponsorBlock categories", SettingsSectionId.SPONSOR,
        "intro", "outro", "filler", "self promotion", "self promo", "interaction", "music",
        "non music", "preview", "recap", "category", "categories", "sponsorblock", "tea",
        "highlight", "tangent"),
    entry("SponsorBlock progress bar", SettingsSectionId.SPONSOR,
        "progress bar", "style", "segments", "share the line", "split", "bar", "sponsorblock"),
    entry("AI mode", SettingsSectionId.ACCOUNTS,
        "ai", "ai mode", "resync", "re sync", "timestamp", "timestamps", "align", "alignment",
        "comment", "comments", "offset", "clock", "cue", "subtitle", "subtitles", "timing",
        "chapters", "summary"),
    entry("Model endpoint (AI)", SettingsSectionId.ACCOUNTS,
        "endpoint", "url", "server", "model", "api", "api key", "key", "token", "llm", "ai",
        "openai", "ollama", "local model", "prompt", "gemini", "anthropic"),
    entry("Post comments", SettingsSectionId.ACCOUNTS,
        "post", "posting", "comment", "comments", "reply", "replies", "account", "google",
        "oauth", "client id", "client secret", "sign in", "sign out", "login", "logout",
        "youtube data api", "credentials"),
    entry("Import", SettingsSectionId.DATA,
        "import", "importing", "restore", "backup", "migrate", "migration", "subs",
        "subscriptions", "subscription", "channels", "newpipe", "libretube", "libre tube",
        "takeout", "google takeout", "csv", "json", "zip", "settings", "preferences", "recipe",
        "recipes", "new phone", "move"),
    entry("Export", SettingsSectionId.DATA,
        "export", "exporting", "backup", "save", "share", "copy", "file", "subs",
        "subscriptions", "subscription", "channels", "settings", "preferences", "recipe",
        "recipes", "new phone", "move"),
    entry("Self-repair", SettingsSectionId.DIAGNOSTICS,
        "repair", "self repair", "selfrepair", "canary", "extractor", "youtube", "repair log",
        "summary", "explain", "how does this work", "diagnostics", "hx"),
    entry("Logs", SettingsSectionId.DIAGNOSTICS,
        "log", "logs", "logging", "diagnostics", "error", "errors", "history", "debug",
        "crash", "crashes", "problem", "trouble", "support"),
    entry("About GlassTube", SettingsSectionId.ABOUT,
        "about", "version", "licence", "license", "gpl", "gplv3", "credits", "app", "source",
        "code", "github", "copyright", "build"),
    // The one search result that starts something. The button is at the bottom of the index, so
    // the result launches the wizard rather than opening About, which has no such control.
    entry("Run setup again", SettingsSectionId.ABOUT,
        "setup", "wizard", "onboarding", "first run", "first launch", "start over", "beginning",
        "theme", "accent", "region", "import", runs = true),
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

/** How much worse a keyword match is than the row's own words. Higher than any single word score. */
private const val KEYWORD_PENALTY = 4

/**
 * A row's own words beat a keyword. Typing "chapters" has to land on the row called Chapters,
 * not on the AI entry that merely mentions chapters in its keywords.
 */
private fun wordScore(word: String, entry: SettingsEntry): Int? {
    scoreIn(word, listOf(entry.title))?.let { return it }
    scoreIn(word, entry.keywords)?.let { return it + KEYWORD_PENALTY }
    // A section's name is a keyword for every row on its page, at the same penalty.
    scoreIn(word, listOf(entry.section.title))?.let { return it + KEYWORD_PENALTY }
    return null
}

private fun scoreIn(word: String, texts: List<String>): Int? {
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
    texts.forEach { text -> tokens(text).forEach(::consider) }
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
