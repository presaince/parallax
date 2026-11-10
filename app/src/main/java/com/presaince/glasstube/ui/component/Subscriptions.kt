/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.glasstube.data.db.GlassTubeDb

/**
 * The channels this phone follows, by lower-cased name. A card colours the name of a channel you
 * already follow differently from one you do not, which is the one thing a subscription changes
 * about a video you are looking at.
 *
 * Names rather than URLs because a listing and a subscription can name the same channel with
 * different URL shapes (`/c/`, `/channel/`, a handle).
 */
@Composable
fun subscribedChannels(db: GlassTubeDb): Set<String> {
    val all by db.subscriptions().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    return all.map { it.name.lowercase() }.toSet()
}
