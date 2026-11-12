/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.sponsorblock

/**
 * How the player's one progress line shows both the playhead and the sponsor segments. One line,
 * three ways to divide it, and the reader picks which. The stored value is the [key], so a label
 * can be reworded without touching anyone's saved choice.
 */
enum class SponsorBarStyle(val key: String, val label: String) {
    /** Segments fill the line; the accent covers the part already watched. */
    COVER("cover", "Covered"),

    /** Segments keep their colour; the watched part carries a wash of the accent over them. */
    TINT("tint", "Tinted"),

    /** Segments fill the line; a thin accent hairline down its centre is the playhead. */
    HAIRLINE("hairline", "Hairline"),
    ;

    companion object {
        val default = COVER

        /** An unknown or missing key is the default, never an exception: it comes from preferences. */
        fun fromKey(stored: String?): SponsorBarStyle =
            entries.firstOrNull { it.key == stored } ?: default
    }
}
