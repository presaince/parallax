/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.presaince.glasstube.MainActivity
import com.presaince.glasstube.GlassTubeApp

/**
 * Keeps the app's single player alive in the foreground and publishes the media notification.
 * Single-process app, so the session points at the container's ExoPlayer instead of copying state
 * across a binder.
 *
 * The notification is posted explicitly on playback events: the service is started rather than
 * bound by a MediaController, so media3's automatic notification path never fires.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.presaince.glasstube.OPEN_PLAYER"
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) updateNotification()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> updateNotification()
                Player.STATE_ENDED, Player.STATE_IDLE -> stopSelfSafely()
                else -> Unit
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = (application as GlassTubeApp).container.playback.player
        mediaSession = MediaSession.Builder(this, sessionPlayer(showRestart()))
            // Without a session activity a notification tap goes through the media-button path and
            // starts the service before it can resolve an activity. This makes it a direct resume.
            .setSessionActivity(openAppIntent())
            .build()
            .also { addSession(it) }
        player.addListener(listener)
        // The service is created by the first play() call, so playback can already be running
        // before the listener exists. Ask once, or the notification never appears.
        if (player.isPlaying) updateNotification()
    }

    /**
     * The notification's far-left button is the restart control. Media3 builds that row from the
     * player's available commands, so removing seek-to-previous is the only reliable way to take
     * it away — a custom button in its place does not get an action on this platform.
     */
    private fun sessionPlayer(showRestart: Boolean): Player {
        if (showRestart) return player
        return object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                val available = super.getAvailableCommands()
                val kept = Player.Commands.Builder()
                for (index in 0 until available.size()) {
                    val command = available.get(index)
                    if (command != Player.COMMAND_SEEK_TO_PREVIOUS &&
                        command != Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
                    ) {
                        kept.add(command)
                    }
                }
                return kept.build()
            }
        }
    }

    private fun showRestart(): Boolean =
        getSharedPreferences("glasstube", MODE_PRIVATE).getBoolean("notification_restart", true)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (player.isPlaying) updateNotification()
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * A swipe out of recents is a close. Without this the foreground service outlived its task —
     * the screen was gone, the notification and the audio were not, and only a force-stop ended
     * it. [PlaybackController.stop] also stops this service and clears the notification.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as GlassTubeApp).container.playback.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        player.removeListener(listener)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun updateNotification() {
        val session = mediaSession ?: return
        // Always foreground: the service was started with startForegroundService, and Android
        // kills a service that never calls startForeground. A paused but loaded player still owns
        // the notification.
        onUpdateNotification(session, true)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** IDLE arrives on the first prepare as well, so only stop when nothing is loaded. */
    private fun stopSelfSafely() {
        if (player.playbackState == Player.STATE_ENDED && !player.isPlaying) {
            // Named: the spontaneous stop was unclassifiable because the log never said the
            // service had stopped itself versus the player going idle on its own.
            Log.i("glasstube-player", "service stopping itself at ENDED pos=${player.currentPosition}ms")
            stopSelf()
        }
    }
}
