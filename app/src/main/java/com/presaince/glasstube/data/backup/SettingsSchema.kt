/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.backup

import com.presaince.glasstube.data.resync.ResyncSettings
import com.presaince.glasstube.data.sponsorblock.SponsorCategories
import com.presaince.glasstube.playback.SoundPreset
import com.presaince.glasstube.playback.PlaybackController
import com.presaince.glasstube.ui.component.CardLayout
import com.presaince.glasstube.ui.theme.Accents
import com.presaince.glasstube.ui.theme.ContrastPolicy
import com.presaince.glasstube.ui.theme.ShuffleCadence
import com.presaince.glasstube.ui.theme.ThemeMode

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

/** One preference: its name in the file, the key it lives under, what it holds, and its default. */
internal data class PrefField(
    val json: String,
    val pref: String,
    val kind: PrefKind,
    /**
     * What the app reads when the pref was never written. A backup has to carry this too: a
     * setting left at its default is still a choice, and a restore that skips it leaves the new
     * phone's own value in place — which is how a fresh install's wizard pick used to survive a
     * restore. The literals are the ones each reader uses; where a constant exists, it is used
     * rather than copied.
     */
    val default: Any,
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
            PrefField("theme", "theme_mode", PrefKind.STRING, ThemeMode.GLASS.name),
            PrefField("glassBase", "glass_base_mode", PrefKind.STRING, ThemeMode.SYSTEM.name),
            PrefField("accent", "accent", PrefKind.STRING, Accents.cyan.name),
            PrefField("contrast", "contrast_policy", PrefKind.STRING, ContrastPolicy.AA.stored),
            PrefField("shuffle", "accent_shuffle", PrefKind.STRING, ShuffleCadence.OFF.name),
            // The glass dial's resting fill; Theme.readGlassLevel uses the same 0.42.
            PrefField("glassLevel", "glass_level_v2", PrefKind.FLOAT, 0.42f),
            PrefField("loadingStyle", "loading_style", PrefKind.INT, 0),
            PrefField("animations", "animations_enabled", PrefKind.BOOL, true),
            PrefField("tintFromVideo", "theme_from_video", PrefKind.BOOL, false),
        ),
        "cards" to listOf(
            PrefField("layout", "card_layout", PrefKind.STRING, CardLayout.SIDE.name),
            PrefField("title", "card_title", PrefKind.BOOL, true),
            PrefField("channel", "card_channel", PrefKind.BOOL, true),
            PrefField("age", "card_age", PrefKind.BOOL, true),
            PrefField("views", "card_views", PrefKind.BOOL, true),
            PrefField("likes", "card_likes", PrefKind.BOOL, true),
            PrefField("duration", "card_duration", PrefKind.BOOL, true),
            PrefField("watches", "card_watches", PrefKind.BOOL, true),
            PrefField("comments", "card_comments", PrefKind.BOOL, true),
            PrefField("progress", "card_progress", PrefKind.BOOL, true),
            PrefField("notes", "card_notes", PrefKind.BOOL, true),
        ),
        "playback" to listOf(
            PrefField("sponsorSkip", "sponsor_skip", PrefKind.BOOL, true),
            PrefField(
                "sponsorCategories",
                "sponsor_categories",
                PrefKind.STRING_SET,
                SponsorCategories.default,
            ),
            PrefField("speed", "playback_speed", PrefKind.FLOAT, 1f),
            PrefField("autoDegrade", "auto_degrade", PrefKind.BOOL, true),
            // Choppy moments in 30 s before the picture gives up a rung. Three is the default:
            // one is what the old rule did, and it read as a bug.
            PrefField("degradeTolerance", "auto_degrade_episodes", PrefKind.INT, 3),
            PrefField("watchThreshold", "watch_threshold", PrefKind.STRING, "30s"),
            // The feed's own cut, in percent: 0 is off. Kept apart from watchThreshold, which
            // decides when an open is a watch rather than when a video leaves the feed.
            PrefField("hideWatchedPercent", "feed_hide_watched_percent", PrefKind.INT, 0),
            // 0 is Auto; PlayerScreen reads the same fallback. Two ladders, one per connection:
            // the Wi-Fi key is the original, the cellular one is the metered ceiling.
            PrefField("quality", "video_quality", PrefKind.INT, 0),
            PrefField("qualityCellular", "video_quality_cellular", PrefKind.INT, 0),
            PrefField(
                "cacheMb",
                "stream_cache_mb",
                PrefKind.INT,
                PlaybackController.DEFAULT_STREAM_CACHE_MB,
            ),
            PrefField("progressInFullscreen", "progress_in_fullscreen", PrefKind.BOOL, true),
            PrefField("seekBaseSeconds", "seek_base_seconds", PrefKind.INT, 5),
            PrefField("pipOnLeave", "pip_on_leave", PrefKind.BOOL, true),
            PrefField("restartButton", "notification_restart", PrefKind.BOOL, true),
            PrefField("chaptersStyle", "chapters_style", PrefKind.INT, 0),
            PrefField("captionsOn", "captions_on", PrefKind.BOOL, false),
            // Empty means "the best track each video offers". Stored as the extractor's language
            // tag ("en", "de"), so a restore reads the same value the player wrote.
            PrefField("captionLanguage", "caption_language", PrefKind.STRING, ""),
            PrefField("captionSize", "caption_size", PrefKind.INT, 1),
            PrefField("playerSections", "player_sections", PrefKind.STRING, ""),
            PrefField("chapterTimestamps", "chapter_timestamps", PrefKind.BOOL, true),
            PrefField("playerLabelsRight", "player_labels_right", PrefKind.BOOL, false),
            PrefField("endRelated", "end_related_enabled", PrefKind.BOOL, true),
            PrefField("hideLive", "hide_live", PrefKind.BOOL, false),
            PrefField("feedLive", "feed_live", PrefKind.BOOL, true),
            // Stored as one comma string; the reader falls back to DEFAULT_PRESETS.
            PrefField(
                "speedPresets",
                "speed_presets",
                PrefKind.STRING,
                PlaybackController.DEFAULT_PRESETS.joinToString(","),
            ),
        ),
        "sound" to listOf(
            PrefField("soundOn", "sound_enabled", PrefKind.BOOL, false),
            PrefField("soundPreset", "sound_preset", PrefKind.STRING, SoundPreset.FLAT.key),
            PrefField("soundLevelDb", "sound_level_db", PrefKind.INT, 0),
            PrefField("evenOut", "sound_even_out", PrefKind.BOOL, false),
        ),
        "feedback" to listOf(
            PrefField("haptics", "haptics_enabled", PrefKind.BOOL, true),
            PrefField("seekCancel", "haptics_seek_cancel", PrefKind.BOOL, true),
            PrefField("speedHome", "haptics_speed_home", PrefKind.BOOL, true),
            PrefField("clamps", "haptics_clamps", PrefKind.BOOL, true),
            PrefField("milestones", "haptics_milestones", PrefKind.BOOL, true),
        ),
        "content" to listOf(
            // Read through readContentRegions and written through writeContentRegions, so the
            // list is validated on both sides and the legacy single-region key stays in step.
            PrefField("regions", "content_regions", PrefKind.REGIONS, emptyList<String>()),
            PrefField("boredSkipSubscribed", "bored_hide_subscribed", PrefKind.BOOL, true),
        ),
        "search" to listOf(
            // History is data, not a choice: an empty list is left out so a restore adds terms
            // rather than clearing the ones this phone already has.
            PrefField("history", "search_history", PrefKind.LINES, emptyList<String>()),
        ),
        "resync" to listOf(
            PrefField("endpoint", "resync_endpoint", PrefKind.STRING, ResyncSettings.DEFAULT_ENDPOINT),
            PrefField("model", "resync_model", PrefKind.STRING, ResyncSettings.DEFAULT_MODEL),
        ),
    )
}
