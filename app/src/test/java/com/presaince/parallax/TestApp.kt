/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax

import android.app.Application

/**
 * The real [ParallaxApp] builds the container and schedules the feed worker on creation. Neither is
 * wanted in a unit test, and WorkManager is not initialised there, so Robolectric is pointed at this
 * bare application instead (see `src/test/resources/robolectric.properties`).
 */
class TestApp : Application()
