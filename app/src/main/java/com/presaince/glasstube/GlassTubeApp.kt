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
        // A no-op until subscriptions exist; scheduling is idempotent.
        FeedScheduler.schedule(this)
    }
}
