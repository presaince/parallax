/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax

import android.app.Application
import coil3.SingletonImageLoader
import com.presaince.parallax.data.library.FeedScheduler
import com.presaince.parallax.di.AppContainer

class ParallaxApp : Application() {

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
