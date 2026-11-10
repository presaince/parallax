/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.core

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy

/**
 * The screenshot that goes with a bug report, captured from the window exactly as oko does it.
 * PixelCopy is used rather than `view.draw` because it captures what is actually on the display,
 * including the video surface.
 *
 * Held in memory until the report is shared; never written to disk unless the user shares it.
 */
object BugShot {

    @Volatile
    var bitmap: Bitmap? = null
        private set

    fun clear() {
        bitmap?.recycle()
        bitmap = null
    }

    /** Must be called on the main thread, before the report screen replaces what is on screen. */
    fun capture(activity: Activity?, onDone: () -> Unit = {}) {
        if (activity == null) {
            onDone()
            return
        }
        val decor = activity.window.decorView
        if (decor.width == 0 || decor.height == 0) {
            onDone()
            return
        }

        val shot = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(
                activity.window,
                shot,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        bitmap?.recycle()
                        bitmap = shot
                    } else {
                        shot.recycle()
                    }
                    onDone()
                },
                Handler(Looper.getMainLooper()),
            )
        }.onFailure {
            shot.recycle()
            onDone()
        }
    }
}
