/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.data.cache

import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.data.model.Page
import org.json.JSONArray
import org.json.JSONObject

/**
 * A page of comments as one cache string. The cache exists so scrolling back through comments
 * inside the TTL costs nothing; it is not a permanent archive and is pruned.
 *
 * [VERSION] is part of the stored object. A page written by an older build — one whose comments
 * carried no date, so the date sort had nothing to order by — is ignored rather than served.
 */
object CommentCodec {

    private const val VERSION = 2

    fun encode(page: Page<Comment>): String = JSONObject()
        .put("v", VERSION)
        .put("next", page.next ?: JSONObject.NULL)
        .put("total", page.total ?: JSONObject.NULL)
        .put("disabled", page.disabled)
        .put(
            "items",
            JSONArray().apply {
                page.items.forEach { comment ->
                    put(
                        JSONObject()
                            .put("id", comment.id)
                            .put("author", comment.author)
                            .put("authorUrl", comment.authorUrl ?: JSONObject.NULL)
                            .put("text", comment.text)
                            .put("likes", comment.likeCount)
                            .put("published", comment.publishedText)
                            .put("publishedAt", comment.publishedAtMs ?: JSONObject.NULL)
                            .put("at", comment.timestampSec ?: JSONObject.NULL)
                            .put("pinned", comment.pinned)
                            .put("replies", comment.replyCount)
                            .put("repliesPage", comment.repliesPage ?: JSONObject.NULL),
                    )
                }
            },
        )
        .toString()

    /** Returns null when the stored value is not a page this app wrote. */
    fun decode(json: String): Page<Comment>? = runCatching {
        val root = JSONObject(json)
        // An older page may be inside its hour and missing fields this build sorts on.
        if (root.optInt("v") != VERSION) return null
        val array = root.optJSONArray("items") ?: return null
        val items = buildList {
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (id.isBlank()) continue
                add(
                    Comment(
                        id = id,
                        author = entry.optString("author"),
                        authorUrl = if (entry.isNull("authorUrl")) {
                            null
                        } else {
                            entry.optString("authorUrl").takeIf { it.isNotBlank() }
                        },
                        text = entry.optString("text"),
                        likeCount = entry.optInt("likes"),
                        publishedText = entry.optString("published"),
                        // A page cached before comments carried their own date reads as null, which
                        // sorts last rather than pretending every comment is from 1970.
                        publishedAtMs = if (entry.isNull("publishedAt") || !entry.has("publishedAt")) {
                            null
                        } else {
                            entry.optLong("publishedAt")
                        },
                        timestampSec = if (entry.isNull("at")) null else entry.optLong("at"),
                        pinned = entry.optBoolean("pinned"),
                        replyCount = entry.optInt("replies"),
                        repliesPage = if (entry.isNull("repliesPage")) {
                            null
                        } else {
                            entry.optString("repliesPage").takeIf { it.isNotBlank() }
                        },
                    ),
                )
            }
        }
        Page(
            items = items,
            next = if (root.isNull("next")) null else root.optString("next").takeIf { it.isNotBlank() },
            total = if (root.isNull("total")) null else root.optLong("total"),
            disabled = root.optBoolean("disabled"),
        )
    }.getOrNull()
}
