/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.data.importer

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export in the shape other clients already read: a NewPipe-style JSON file with the same URLs
 * the importer accepts. No app state beyond the list, because a subscription list is the thing
 * worth carrying.
 */
object SubscriptionExporter {

    fun newPipeJson(subscriptions: List<ImportedSubscription>): String = JSONObject()
        .put("app_version", "0.1.0")
        .put("app_version_int", 1)
        .put(
            "subscriptions",
            JSONArray().apply {
                subscriptions.forEach { subscription ->
                    put(
                        JSONObject()
                            .put("service_id", 0)
                            .put("url", subscription.url)
                            .put("name", subscription.name),
                    )
                }
            },
        )
        .toString(2)
}

/**
 * NewPipe backups are a zip with `subscriptions.json` inside. Reading it here keeps the importer
 * one function for the UI: bytes in, subscriptions out.
 */
object SubscriptionZip {

    fun findSubscriptionsJson(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("subscriptions.json")) {
                    return@use zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
            null
        }
    }.getOrNull()
}
