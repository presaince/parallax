/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.library

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.presaince.glasstube.GlassTubeApp
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Feed polling. WorkManager has no jitter for periodic work, so this schedules one run at a time
 * and reschedules on the way out. Four hours ± thirty minutes keeps the request pattern off a
 * fixed beat without pretending a video feed needs push.
 */
class FeedWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GlassTubeApp).container
        val refresh = container.content.refreshFeed()

        // Every channel failed: the network or the extractor is down. Retry with backoff instead
        // of scheduling the normal four-hour run, or the two paths fight over the unique work.
        if (refresh.channels > 0 && refresh.failures == refresh.channels) return Result.retry()

        FeedScheduler.schedule(applicationContext)
        return Result.success()
    }
}

object FeedScheduler {

    private const val WORK_NAME = "feed-refresh"

    fun schedule(context: Context, random: Random = Random.Default) {
        val request = OneTimeWorkRequestBuilder<FeedWorker>()
            .setInitialDelay(delayMs(random), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** Four hours, shaken by up to half an hour either way. Visible for tests. */
    fun delayMs(random: Random): Long {
        val jitter = random.nextLong(-JITTER_MS, JITTER_MS + 1)
        return INTERVAL_MS + jitter
    }

    private const val INTERVAL_MS = 4L * 60 * 60 * 1000
    private const val JITTER_MS = 30L * 60 * 1000
}
