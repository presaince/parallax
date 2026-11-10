/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.sponsorblock

/** The categories this app knows how to skip, with the names SponsorBlock uses. */
object SponsorCategories {
    const val SPONSOR = "sponsor"
    const val SELF_PROMO = "selfpromo"
    const val INTERACTION = "interaction"
    const val INTRO = "intro"
    const val OUTRO = "outro"
    const val PREVIEW = "preview"
    const val MUSIC_OFFTOPIC = "music_offtopic"
    const val FILLER = "filler"

    val all = listOf(
        SPONSOR,
        SELF_PROMO,
        INTERACTION,
        INTRO,
        OUTRO,
        PREVIEW,
        MUSIC_OFFTOPIC,
        FILLER,
    )

    val default = setOf(SPONSOR, SELF_PROMO, INTERACTION, INTRO, OUTRO)

    fun label(category: String): String = when (category) {
        SPONSOR -> "Sponsor"
        SELF_PROMO -> "Self-promotion"
        INTERACTION -> "Interaction reminder"
        INTRO -> "Intro"
        OUTRO -> "Outro"
        PREVIEW -> "Preview or recap"
        MUSIC_OFFTOPIC -> "Non-music section"
        FILLER -> "Filler"
        else -> category
    }

    /** One canonical colour per category, so the timeline, the rows and Settings agree. */
    fun color(category: String): androidx.compose.ui.graphics.Color = when (category) {
        SPONSOR -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        SELF_PROMO -> androidx.compose.ui.graphics.Color(0xFF69F0AE)
        INTERACTION -> androidx.compose.ui.graphics.Color(0xFFFF4081)
        INTRO -> androidx.compose.ui.graphics.Color(0xFFBDBDBD)
        OUTRO -> androidx.compose.ui.graphics.Color(0xFFBDBDBD)
        PREVIEW -> androidx.compose.ui.graphics.Color(0xFF4FC3F7)
        MUSIC_OFFTOPIC -> androidx.compose.ui.graphics.Color(0xFFBA68C8)
        FILLER -> androidx.compose.ui.graphics.Color(0xFF9575CD)
        else -> androidx.compose.ui.graphics.Color(0xFFBDBDBD)
    }
}
