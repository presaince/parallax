/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.backup

/** How a preference is stored, and therefore how it is written in a backup file. */
internal enum class PrefKind {
    BOOL,
    INT,
    FLOAT,
    STRING,
    STRING_SET,

    /** Newline-separated in prefs, a JSON array in the file: search history. */
    LINES,

    /** A validated list written through its own helper: the content regions. */
    REGIONS,
}

/** One preference: its name in the file, the key it lives under, and what it holds. */
internal data class PrefField(
    val json: String,
    val pref: String,
    val kind: PrefKind,
)

/**
 * One table, walked in both directions: export reads each field, import writes it back. A field
 * cannot be in the file and unknown to the restore, or the other way round.
 *
 * The values are the ones the app stores, not pretty labels — `"theme_mode": "GLASS"`, not
 * `"theme": "Glass"`. A backup that round-trips is worth more than one that reads nicely.
 *
 * Deliberately absent: every secret (the Resync key lives in the Keystore, the OAuth tokens in
 * encrypted prefs), the update state (`apkUrl`, `versionCode`), and the period key the accent
 * shuffle uses (`accent_day`), which belongs to a clock, not to a reader.
 */
internal object SettingsSchema {

    val groups: Map<String, List<PrefField>> = linkedMapOf(
        "appearance" to listOf(
            PrefField("theme", "theme_mode", PrefKind.STRING),
            PrefField("glassBase", "glass_base_mode", PrefKind.STRING),
            PrefField("accent", "accent", PrefKind.STRING),
            PrefField("contrast", "contrast_policy", PrefKind.STRING),
            PrefField("shuffle", "accent_shuffle", PrefKind.STRING),
            PrefField("solid", "solid_surfaces", PrefKind.BOOL),
            PrefField("glassLevel", "glass_level_v2", PrefKind.FLOAT),
            PrefField("loadingStyle", "loading_style", PrefKind.INT),
            PrefField("animations", "animations_enabled", PrefKind.BOOL),
            PrefField("tintFromVideo", "theme_from_video", PrefKind.BOOL),
        ),
        "cards" to listOf(
            PrefField("layout", "card_layout", PrefKind.STRING),
            PrefField("title", "card_title", PrefKind.BOOL),
            PrefField("channel", "card_channel", PrefKind.BOOL),
            PrefField("age", "card_age", PrefKind.BOOL),
            PrefField("views", "card_views", PrefKind.BOOL),
            PrefField("likes", "card_likes", PrefKind.BOOL),
            PrefField("duration", "card_duration", PrefKind.BOOL),
            PrefField("watches", "card_watches", PrefKind.BOOL),
            PrefField("comments", "card_comments", PrefKind.BOOL),
            PrefField("progress", "card_progress", PrefKind.BOOL),
            PrefField("notes", "card_notes", PrefKind.BOOL),
        ),
        "playback" to listOf(
            PrefField("sponsorSkip", "sponsor_skip", PrefKind.BOOL),
            PrefField("sponsorCategories", "sponsor_categories", PrefKind.STRING_SET),
            PrefField("speed", "playback_speed", PrefKind.FLOAT),
            PrefField("watchThreshold", "watch_threshold", PrefKind.STRING),
            PrefField("quality", "video_quality", PrefKind.INT),
            PrefField("cacheMb", "stream_cache_mb", PrefKind.INT),
            PrefField("progressInFullscreen", "progress_in_fullscreen", PrefKind.BOOL),
            PrefField("seekBaseSeconds", "seek_base_seconds", PrefKind.INT),
            PrefField("pipOnLeave", "pip_on_leave", PrefKind.BOOL),
            PrefField("restartButton", "notification_restart", PrefKind.BOOL),
            PrefField("chaptersHorizontal", "chapters_horizontal", PrefKind.BOOL),
            PrefField("chapterTimestamps", "chapter_timestamps", PrefKind.BOOL),
            PrefField("playerLabelsRight", "player_labels_right", PrefKind.BOOL),
            PrefField("endRelated", "end_related_enabled", PrefKind.BOOL),
            PrefField("hideLive", "hide_live", PrefKind.BOOL),
        ),
        "feedback" to listOf(
            PrefField("haptics", "haptics_enabled", PrefKind.BOOL),
            PrefField("seekCancel", "haptics_seek_cancel", PrefKind.BOOL),
            PrefField("speedHome", "haptics_speed_home", PrefKind.BOOL),
            PrefField("clamps", "haptics_clamps", PrefKind.BOOL),
            PrefField("milestones", "haptics_milestones", PrefKind.BOOL),
        ),
        "content" to listOf(
            // Read through readContentRegions and written through writeContentRegions, so the
            // list is validated on both sides and the legacy single-region key stays in step.
            PrefField("regions", "content_regions", PrefKind.REGIONS),
            PrefField("boredSkipSubscribed", "bored_hide_subscribed", PrefKind.BOOL),
        ),
        "search" to listOf(
            PrefField("history", "search_history", PrefKind.LINES),
        ),
        "resync" to listOf(
            PrefField("endpoint", "resync_endpoint", PrefKind.STRING),
            PrefField("model", "resync_model", PrefKind.STRING),
        ),
    )
}
