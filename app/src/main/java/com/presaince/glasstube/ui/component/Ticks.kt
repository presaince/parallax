package com.presaince.glasstube.ui.component

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Whether a tick may fire: the app's own setting and the system's must both allow it. */
fun hapticsAllowed(appEnabled: Boolean, systemEnabled: Boolean): Boolean = appEnabled && systemEnabled

/** The system haptic setting, read fresh each time so a change applies at once. */
fun systemHapticsOn(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1

/**
 * The cancel snap: a drag's displacement was away from zero and has come back across
 * zero — landing on it or jumping over it. Zero here is the gesture's own start, never
 * the start of the video, so it reads as "you asked for nothing after all" rather than a
 * position. A drag moves in jumps, so exact equality almost never occurs; the crossing
 * is the event.
 */
fun seekCancel(fromDeltaMs: Long, toDeltaMs: Long): Boolean {
    if (fromDeltaMs == 0L || fromDeltaMs == toDeltaMs) return false
    return toDeltaMs == 0L || (fromDeltaMs > 0L) != (toDeltaMs > 0L)
}

/**
 * Whether a speed change from one value to another crossed the resting value, 1.0x,
 * landing on it or jumping over it. Fast drags step over 1.0x without stopping, so
 * equality alone misses the return.
 */
fun speedHomeCross(fromTenths: Int, toTenths: Int): Boolean {
    if (fromTenths == toTenths || fromTenths == 10) return false
    return (fromTenths < 10 && toTenths >= 10) || (fromTenths > 10 && toTenths <= 10)
}

/** Whether a move from one position to another crossed a marked point between them. */
fun crossedMilestone(fromMs: Long, toMs: Long, marksMs: List<Long>): Boolean {
    if (fromMs == toMs) return false
    val low = minOf(fromMs, toMs)
    val high = maxOf(fromMs, toMs)
    return marksMs.any { it > low && it < high }
}

/**
 * Whether a value reached an end it was not already at: volume or brightness hitting the
 * top or the bottom of its range. Already-at-the-end movement does not tick again.
 */
fun clampHit(previous: Float, next: Float, low: Float, high: Float): Boolean {
    if (previous == next) return false
    return (next <= low && previous > low) || (next >= high && previous < high)
}

/**
 * One place to fire ticks from. The master setting gates first, then each feature's own
 * setting, then the system's haptic setting, and the vibration is tagged for touch
 * usage so the phone applies its intensity on top. With any of them off, nothing fires.
 */
class TickRunner(private val context: Context) {
    private fun pref(name: String): Boolean =
        context.getSharedPreferences("glasstube", Context.MODE_PRIVATE).getBoolean(name, true)

    private fun fire(effect: Int) {
        if (!hapticsAllowed(pref("haptics_enabled"), systemHapticsOn(context))) return
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val attributes = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_TOUCH)
            .build()
        vibrator.vibrate(VibrationEffect.createPredefined(effect), attributes)
    }

    /** A test tick, for the settings switch itself. */
    fun step() = fire(VibrationEffect.EFFECT_CLICK)

    private fun strong() = fire(VibrationEffect.EFFECT_DOUBLE_CLICK)

    private fun light() = fire(VibrationEffect.EFFECT_CLICK)

    /** The cancel snap: displacement back at zero after straying away from it. */
    fun cancelSnap(fromDeltaMs: Long, toDeltaMs: Long) {
        if (pref("haptics_seek_cancel") && seekCancel(fromDeltaMs, toDeltaMs)) strong()
    }

    /** The snap back at 1.0x, the speed's resting value. */
    fun speedHome(fromTenths: Int, toTenths: Int) {
        if (pref("haptics_speed_home") && speedHomeCross(fromTenths, toTenths)) strong()
    }

    /** Reaching an end of volume or brightness. */
    fun clamp(previous: Float, next: Float, low: Float, high: Float) {
        if (pref("haptics_clamps") && clampHit(previous, next, low, high)) strong()
    }

    /** Crossing a marked point while seeking, such as a chapter boundary. */
    fun milestone(fromMs: Long, toMs: Long, marksMs: List<Long>) {
        if (pref("haptics_milestones") && crossedMilestone(fromMs, toMs, marksMs)) light()
    }
}

@Composable
fun rememberTicks(): TickRunner {
    val context = LocalContext.current
    return remember(context) { TickRunner(context) }
}
