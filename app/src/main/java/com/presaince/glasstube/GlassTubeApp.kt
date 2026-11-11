/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube

import android.app.Application
import coil3.SingletonImageLoader
import com.presaince.glasstube.data.library.FeedScheduler
import com.presaince.glasstube.di.AppContainer

class GlassTubeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SingletonImageLoader.setSafe { container.imageLoader }
        installCrashLog()
        // The newest exit the OS remembers and we have not written yet: a native crash, an ANR or
        // a low-memory kill left nothing in our own log, and this is the only way to know.
        container.exitLog.record()
        // A no-op until subscriptions exist; scheduling is idempotent.
        FeedScheduler.schedule(this)
    }

    /**
     * The last thing the app does before it dies: name the exception in the log a report carries.
     * An uncaught exception used to leave nothing behind, so a crash and a kill looked the same.
     */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                container.log.error(
                    "crash",
                    "${thread.name}: ${throwable.javaClass.name}: ${throwable.message} at " +
                        throwable.stackTrace.take(8).joinToString(" | ") { it.toString() },
                )
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
