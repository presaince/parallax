/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.ui.component

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * How a card is laid out. Classic is the full-width picture with the words underneath, Side puts
 * the picture on the left and the words beside it, Minimal is a short row for people who only scan
 * titles. The preview in Settings draws all three, so the choice is never a guess.
 */
enum class CardLayout(val label: String) {
    SIDE("Side"),
    CLASSIC("Classic"),
    MINIMAL("Minimal"),
}

/**
 * What a video card shows. A card with everything is noise for someone who scans thumbnails, and
 * a card with nothing is a wall of pictures; the choice belongs to the reader.
 */
data class CardStyle(
    val layout: CardLayout = CardLayout.SIDE,
    val title: Boolean = true,
    val channel: Boolean = true,
    val age: Boolean = true,
    val views: Boolean = true,
    val likes: Boolean = true,
    val duration: Boolean = true,
) {
    companion object {
        private const val PREFS = "parallax"

        /**
         * Bumped on every write. Cards already on screen stay composed while Settings is open, so
         * they need a reason to be recomposed: this is it.
         */
        var revision by mutableIntStateOf(0)
            private set

        private val keys = listOf(
            "card_layout",
            "card_title",
            "card_channel",
            "card_age",
            "card_views",
            "card_likes",
            "card_duration",
        )

        fun read(context: Context): CardStyle {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return CardStyle(
                layout = runCatching {
                    CardLayout.valueOf(prefs.getString("card_layout", CardLayout.SIDE.name)!!)
                }.getOrDefault(CardLayout.SIDE),
                title = prefs.getBoolean("card_title", true),
                channel = prefs.getBoolean("card_channel", true),
                age = prefs.getBoolean("card_age", true),
                views = prefs.getBoolean("card_views", true),
                likes = prefs.getBoolean("card_likes", true),
                duration = prefs.getBoolean("card_duration", true),
            )
        }

        fun write(context: Context, style: CardStyle) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("card_layout", style.layout.name)
                .putBoolean("card_title", style.title)
                .putBoolean("card_channel", style.channel)
                .putBoolean("card_age", style.age)
                .putBoolean("card_views", style.views)
                .putBoolean("card_likes", style.likes)
                .putBoolean("card_duration", style.duration)
                .apply()
            revision += 1
        }

        /** For the settings list: the storage keys, in the same order as the fields. */
        fun keysInOrder(): List<String> = keys
    }
}
