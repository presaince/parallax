/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import android.content.pm.ActivityInfo
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.presaince.parallax.playback.PlaybackService
import com.presaince.parallax.ui.nav.ParallaxNav
import com.presaince.parallax.ui.theme.ParallaxTheme
import com.presaince.parallax.ui.theme.isDarkTheme
import com.presaince.parallax.ui.theme.readThemeMode
import com.presaince.parallax.ui.theme.rememberThemeController

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tap on the media notification asks for the playing video's full player. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER, false) != true) return
        // Cleared, so a rotation does not ask again.
        intent.removeExtra(PlaybackService.EXTRA_OPEN_PLAYER)
        (application as ParallaxApp).container.playback.requestOpenPlayer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The manifest no longer locks the orientation. Without this the app would rotate
        // everywhere; the player alone asks for landscape when it goes fullscreen or the phone is
        // turned sideways, and it restores portrait when it leaves.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val dark = isDarkTheme(readThemeMode(this), this)
        // The bar icons have to match the app's background, not the system's.
        enableEdgeToEdge(
            statusBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (dark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
        )
        askForNotificationPermission()

        val container = (application as ParallaxApp).container
        handleIntent(intent)
        setContent {
            val theme = rememberThemeController()
            // The bar icons follow the app's theme, not the system's.
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !theme.dark
                controller.isAppearanceLightNavigationBars = !theme.dark
            }
            ParallaxTheme(
                mode = theme.mode,
                accent = theme.accent,
                solidSurfaces = theme.solidSurfaces,
                glassDark = theme.glassDark,
                glassLevel = theme.glassLevel,
                contrast = theme.contrast,
            ) {
                ParallaxNav(container = container, theme = theme)
            }
        }
    }

    /**
     * Leaving the app while a video plays should keep the video visible, not stop it. PiP is the
     * reason the manifest declares the activity as PiP-capable; without a playing video there is
     * nothing to shrink, so the system gets an ordinary background.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val enabled = getSharedPreferences("parallax", MODE_PRIVATE)
            .getBoolean("pip_on_leave", true)
        if (!enabled) return
        val player = (application as ParallaxApp).container.playback.player
        if (!player.isPlaying) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    /** The media notification is the player, and from API 33 it needs an explicit grant. */
    private fun askForNotificationPermission() {
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
