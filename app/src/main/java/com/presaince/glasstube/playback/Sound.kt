/*
 * GlassTube â€” Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import com.presaince.glasstube.core.AppLog

/**
 * The five bands the equaliser works in, in hertz. Fixed because the effect's band edges are
 * chosen when it is built, and a phone screen has no room to ask for others.
 */
val SOUND_BANDS_HZ = listOf(60f, 230f, 910f, 3600f, 14000f)

/**
 * Four shapes a person actually asks for. Flat is the same as the effect being off, and it is
 * kept in the list so "back to normal" is one tap rather than a guess at which one was normal.
 */
enum class SoundPreset(val key: String, val label: String, val gainsDb: List<Float>) {
    FLAT("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    VOICE("voice", "Voice", listOf(-4f, 3f, 4f, 1f, -2f)),
    BASS("bass", "Bass", listOf(5f, 3f, 0f, -1f, -2f)),
    TREBLE("treble", "Treble", listOf(-2f, -1f, 0f, 3f, 4f));

    companion object {
        fun fromKey(key: String?): SoundPreset =
            entries.firstOrNull { it.key == key } ?: FLAT
    }
}

/** The most the level control may add. Above this the limiter is working on every word. */
const val SOUND_MAX_LEVEL_DB = 12

/**
 * Everything the sound page chooses. Off is the default and means the effects are not configured
 * at all: an app that colours the sound before it is asked to is a fault, not a feature.
 */
data class SoundSettings(
    val enabled: Boolean = false,
    val preset: SoundPreset = SoundPreset.FLAT,
    /** Extra gain in dB, 0..[SOUND_MAX_LEVEL_DB]. */
    val levelDb: Int = 0,
    /** Compressor plus limiter: quiet speech and loud music closer to one level. */
    val evenOut: Boolean = false,
)

/** dB to the millibels LoudnessEnhancer takes, clamped to what the control offers. */
fun levelToMillibels(levelDb: Int): Int = levelDb.coerceIn(0, SOUND_MAX_LEVEL_DB) * 100

/**
 * The owner of the audio effects. One instance lives as long as the player, attached to its
 * audio session, and every change is applied through [apply].
 *
 * Effects are hardware, and a device is allowed to refuse them. A refusal costs the sound
 * shaping and never the video, so each attach is its own `runCatching` with one log line rather
 * than a reason for playback to fail.
 */
class AudioTuning(private val log: AppLog) {

    private var equalizer: DynamicsProcessing? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId: Int = 0

    /** Binds the effects to the player's own session. Called once, with a session we chose. */
    fun attach(session: Int) {
        if (session <= 0 || session == sessionId) return
        release()
        sessionId = session

        runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, // both channels; mono is duplicated by the sink
                false, 0, // no pre-EQ: nothing to apply before the bands
                true, 1, // one-band compressor for "even out volume"
                true, SOUND_BANDS_HZ.size,
                true, // the limiter, so a boosted band cannot clip
            ).build()
            DynamicsProcessing(0, session, config).also {
                it.enabled = true
                equalizer = it
            }
        }.onFailure {
            log.warn("sound", "equaliser unavailable: ${it.javaClass.simpleName}: ${it.message}")
        }

        runCatching {
            LoudnessEnhancer(session).also { loudness = it }
        }.onFailure {
            log.warn("sound", "level unavailable: ${it.javaClass.simpleName}: ${it.message}")
        }
        log.info("sound", "effects attached to session $session")
    }

    /**
     * Writes the settings into the effects. A disabled [SoundSettings] flattens everything rather
     * than leaving the last shape in place: turning the equaliser off means off.
     */
    fun apply(settings: SoundSettings) {
        val gains = if (settings.enabled) settings.preset.gainsDb else SoundPreset.FLAT.gainsDb
        equalizer?.let { effect ->
            runCatching {
                // The effect exposes per-channel copies only, so channel 0 is read as the shape
                // and then pushed to both channels.
                val eq = effect.getPostEqByChannelIndex(0)
                for (band in 0 until minOf(gains.size, eq.bandCount)) {
                    val eqBand = eq.getBand(band)
                    eqBand.gain = gains[band]
                    effect.setPostEqBandAllChannelsTo(band, eqBand)
                }
                val mbc = effect.getMbcByChannelIndex(0)
                mbc.isEnabled = settings.enabled && settings.evenOut
                if (settings.enabled && settings.evenOut) {
                    val band = mbc.getBand(0)
                    // A gentle full-band compressor: speech lifts, music settles, and the
                    // limiter below stops the makeup gain from turning peaks into clipping.
                    band.threshold = -20f
                    band.ratio = 3f
                    band.attackTime = 10f
                    band.releaseTime = 250f
                    band.postGain = 3f
                    mbc.setBand(0, band)
                }
                effect.setMbcAllChannelsTo(mbc)

                val limiter = effect.getLimiterByChannelIndex(0)
                limiter.isEnabled = true
                limiter.threshold = -1f
                limiter.ratio = 20f
                limiter.attackTime = 5f
                limiter.releaseTime = 60f
                effect.setLimiterAllChannelsTo(limiter)
            }.onFailure {
                log.warn("sound", "equaliser write failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }

        loudness?.let { enhancer ->
            runCatching {
                val millibels = if (settings.enabled) levelToMillibels(settings.levelDb) else 0
                enhancer.setTargetGain(millibels)
                enhancer.enabled = millibels > 0
            }.onFailure {
                log.warn("sound", "level write failed: ${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    /** Gives the effects back before a new session or a teardown takes their session away. */
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
        sessionId = 0
    }
}
