/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.core

/**
 * One stop on the quality ladder. `height` is the ceiling handed to the extractor, so it is a
 * pixel count and never a promise; `label` is what a person reads, because 2160 is a number and
 * "4K" is a fact.
 *
 * Auto is height 0, which the extractor already reads as "best this phone can decode". AI sim is
 * [Int.MAX_VALUE]: a real ceiling of none, named for what it pretends to be. Nothing else rounds
 * it down, because there is nothing above it to pick.
 */
data class QualityStop(
    val height: Int,
    val label: String,
    val short: String,
    val note: String? = null,
)

val QUALITY_STOPS: List<QualityStop> = listOf(
    QualityStop(0, "Auto", "Auto", "the best this phone can decode"),
    QualityStop(240, "240p", "240"),
    QualityStop(480, "480p", "480"),
    QualityStop(1080, "Full HD", "FHD"),
    QualityStop(1440, "QHD", "QHD"),
    QualityStop(2160, "4K", "4K"),
    QualityStop(3240, "6K", "6K"),
    QualityStop(Int.MAX_VALUE, "AI sim", "AI", "ai simulation = 1:1 with reality"),
)

/**
 * First-load height cap for Auto. A chosen height wins as-is; Auto (0) caps at 720p so the
 * first frame needs about a third of the bytes of a 1080p/4K highest-bitrate pick. The cap is
 * at or below every QUALITY_STOPS height except 240/480, so low caps stay exact.
 */
fun firstLoadHeight(storedHeight: Int): Int =
    if (storedHeight == 0) FIRST_LOAD_AUTO_HEIGHT else storedHeight

private const val FIRST_LOAD_AUTO_HEIGHT = 720

/** What to call a stored height. Unknown values keep their number rather than pretending. */
fun qualityLabel(height: Int): String =
    QUALITY_STOPS.firstOrNull { it.height == height }?.label ?: "${height}p"
