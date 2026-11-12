/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

/**
 * One stop on the quality ladder. `height` is the ceiling handed to the extractor, so it is a
 * pixel count and never a promise; `label` is what a person reads, because 2160 is a number and
 * "4K" is a fact.
 *
 * The heights are the ones YouTube actually serves, low to high, and Auto is last: the track
 * climbs left to right and Auto is the top of it, "the best this phone can decode". Auto is
 * height 0, which the extractor already reads as "no ceiling". There is no 6K or 8K stop and no
 * "AI sim": this phone cannot decode either, so they were stops that failed rather than stops
 * that were merely pointless.
 */
data class QualityStop(
    val height: Int,
    val label: String,
    val short: String,
    val note: String? = null,
)

val QUALITY_STOPS: List<QualityStop> = listOf(
    QualityStop(144, "144p", "144", "the smallest picture YouTube serves"),
    QualityStop(240, "240p", "240"),
    QualityStop(360, "360p", "360"),
    QualityStop(480, "480p", "480"),
    QualityStop(720, "HD", "720"),
    QualityStop(1080, "Full HD", "FHD"),
    QualityStop(1440, "QHD", "QHD"),
    QualityStop(2160, "4K", "4K"),
    QualityStop(0, "Auto", "Auto", "starts at 720p, then whatever you pick"),
)

/**
 * The stops worth offering on a screen whose short side is [panelShortSidePx]: the full ladder
 * when the panel is unknown (0), otherwise what the panel can actually show. A 1080p phone stops
 * at Full HD, because QHD and 4K are bytes spent on lines the screen does not have.
 */
fun qualityStops(panelShortSidePx: Int): List<QualityStop> {
    if (panelShortSidePx <= 0) return QUALITY_STOPS
    return QUALITY_STOPS.filter { it.height == 0 || it.height <= panelShortSidePx }
}

/**
 * The stop a stored height lands on once the ladder is capped at the panel: an exact stop stays,
 * a taller one falls to the tallest the panel offers, and anything below 144 falls to Auto.
 * Pure, so the migration of an old 4K choice has a test without a device.
 */
fun snapToLadder(storedHeight: Int, panelShortSidePx: Int): Int {
    if (storedHeight == 0) return 0
    val offered = qualityStops(panelShortSidePx).filter { it.height > 0 }
    if (offered.any { it.height == storedHeight }) return storedHeight
    return offered.lastOrNull { it.height <= storedHeight }?.height ?: 0
}

/**
 * Height cap for a video's first load. A chosen stop is kept — the reader asked for it. Auto starts
 * at 720p on **every** connection, because on Wi-Fi it used to mean "uncapped", which on this phone
 * is Full HD video plus a separate audio track, two connections and roughly twice the bytes before
 * the first frame. That is what turned an opening into a fifteen-second wait (`115657`). The pill
 * still reports what is actually playing, so Auto (720p) says what it did.
 */
const val FIRST_LOAD_HEIGHT = 720

fun firstLoadHeight(storedHeight: Int): Int =
    if (storedHeight != 0) storedHeight else FIRST_LOAD_HEIGHT

/**
 * Where a chosen stop is stored. Two ladders, one per kind of connection: the same phone wants a
 * lower ceiling on mobile data than on Wi-Fi, and the pick is made when streams are fetched. The
 * Wi-Fi key is the original "video_quality", so a choice stored before this existed is kept.
 */
fun qualityKey(transport: Transport): String =
    if (transport == Transport.Cellular) KEY_QUALITY_CELLULAR else KEY_QUALITY_WIFI

/** The stop in force on [transport]. Pure, so the rule has a test without a device. */
fun qualityFor(transport: Transport, wifiHeight: Int, cellularHeight: Int): Int =
    if (transport == Transport.Cellular) cellularHeight else wifiHeight

const val KEY_QUALITY_WIFI = "video_quality"
const val KEY_QUALITY_CELLULAR = "video_quality_cellular"

/**
 * A rung this session stepped down to because the connection could not carry the chosen one. It is
 * separate from the reader's choice on purpose: a bad ten minutes on a train must not rewrite the
 * ceiling they picked, so the ladder lowers this and the picker clears it.
 */
const val KEY_QUALITY_SESSION = "video_quality_session"

/** What to actually ask for: a session step-down outranks the stored choice. Pure. */
fun qualityInForce(storedHeight: Int, sessionHeight: Int): Int =
    if (sessionHeight > 0) sessionHeight else storedHeight

/**
 * The stop one step down the ladder from [height], for a video that keeps stalling. Auto is not a
 * step: it is a ceiling, and the honest answer to "this is too much for this connection" is a
 * height somebody can name. Null means there is nothing lower to offer, so the caller says so
 * rather than pretending it improved.
 */
fun lowerStop(height: Int, stops: List<QualityStop>): QualityStop? {
    val offered = stops.filter { it.height > 0 }.sortedBy { it.height }
    if (offered.isEmpty()) return null
    // Auto is a ceiling nobody can name, so its step down is the stop a phone can hold on a poor
    // connection rather than the smallest picture the ladder offers.
    if (height <= 0) return offered.lastOrNull { it.height <= 720 }
    return offered.lastOrNull { it.height < height }
}

/**
 * The runway ahead of the playhead, in wall-clock milliseconds, below which a stream is choppy.
 * Wall clock and not media time, because the buffer drains at the playback speed: at 2x the same
 * six seconds of buffer is three seconds of picture. That is the whole of why 2x buffers where 1x
 * does not, and why a rule measured in media time can never see it.
 */
const val CHOPPY_RUNWAY_MS = 6_000L

/**
 * How long a short runway must hold before it counts. A fresh load and a seek both start with
 * nothing buffered, and neither is choppy water — the caller only feeds samples from a stream
 * that has stopped growing.
 */
const val CHOPPY_HOLD_MS = 3_000L

/**
 * Whether the picture is being fed slower than it is being played: what is buffered ahead of the
 * playhead, divided by the speed it drains at, is a shorter lead than [CHOPPY_RUNWAY_MS]. Pure,
 * so the 2x case has a test without a device.
 */
fun choppyWater(forwardBufferMs: Long, speed: Float): Boolean {
    val rate = speed.coerceIn(0.25f, 4f)
    return (forwardBufferMs / rate).toLong() < CHOPPY_RUNWAY_MS
}

/**
 * The window a choppy episode is judged in. One thin moment is noise — the player recovers and
 * nobody notices. Several inside half a minute is a connection that cannot carry the speed, and
 * acting on the first one is what made the old rule read as a bug.
 */
const val DEGRADE_WINDOW_MS = 30_000L

/** Choppy moments tolerated inside the window before the picture steps down. */
const val DEGRADE_TOLERANCE_DEFAULT = 3
const val DEGRADE_TOLERANCE_MAX = 10

/** The stamps that can still change the next answer: anything older is outside the window. */
fun freshEpisodes(episodes: List<Long>, nowMs: Long): List<Long> =
    episodes.filter { it > nowMs - DEGRADE_WINDOW_MS }

/**
 * Whether this episode is one too many. Pure, so the patience rule has a test without a device.
 * A tolerance below one is still one: a rule that always fires is not a setting.
 */
fun shouldDegrade(episodes: List<Long>, nowMs: Long, tolerance: Int): Boolean =
    freshEpisodes(episodes, nowMs).size >= tolerance.coerceIn(1, DEGRADE_TOLERANCE_MAX)

/**
 * What to call a stored height, given what ExoPlayer actually resolved. A chosen stop is a request,
 * and the stream behind it can be shorter: "Full HD (720p)" is the honest sentence, where a bare
 * "Full HD" is a claim the picture never made. Auto says what it resolved to for the same reason.
 * Unknown values keep their number rather than pretending.
 */
fun qualityLabel(height: Int, resolvedHeight: Int = 0): String {
    val stop = QUALITY_STOPS.firstOrNull { it.height == height }
    val requested = stop?.label ?: "${height}p"
    if (height == 0) return if (resolvedHeight > 0) "Auto (${resolvedHeight}p)" else "Auto"
    return if (resolvedHeight > 0 && resolvedHeight != height) {
        "$requested (${resolvedHeight}p)"
    } else {
        requested
    }
}
