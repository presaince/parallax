/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.comments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presaince.parallax.R
import com.presaince.parallax.core.formatDuration
import com.presaince.parallax.core.plural
import com.presaince.parallax.data.model.Comment

/**
 * Comments render inside the player page, not on a screen of their own: reading what people said
 * about the second you are watching should not mean leaving the video.
 *
 * Posting appears only when the user has turned it on and signed in, so a viewer never sees a
 * compose box they cannot use.
 */
@Composable
fun CommentsSection(
    viewModel: CommentsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        // The header opens and closes the section, like a description: the count is the label.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = viewModel::toggleExpanded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                // A count with no comments under it is a promise the section cannot keep. Show the
                // total only once there is a page to show it with.
                text = state.total
                    ?.takeIf { it > 0 && (state.items.isNotEmpty() || state.loading) }
                    ?.let { "Comments ($it)" }
                    ?: "Comments",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (state.expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (state.expanded) {
                    "Collapse comments"
                } else {
                    "Expand comments"
                },
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        if (!state.expanded) return@Column

        if (state.resyncReady) {
            TextButton(
                onClick = viewModel::align,
                enabled = !state.aligning && state.items.isNotEmpty(),
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(if (state.aligning) "Aligning…" else "Align timestamps")
            }
        }

        if (state.postingEnabled && state.signedIn && !state.commentsDisabled) {
            val replyingTo = state.replyTo
            if (replyingTo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Replying to ${replyingTo.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.replyTo(null) }) { Text("Cancel") }
                }
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text(if (replyingTo == null) "Add a comment" else "Write a reply") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = viewModel::post,
                    enabled = !state.posting && state.draft.isNotBlank(),
                ) {
                    Text(if (state.posting) "Posting…" else "Post")
                }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            )
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 6.dp),
            )
            TextButton(onClick = viewModel::loadFirstPage) { Text("Try again") }
        }

        if (state.items.isEmpty() && !state.loading && state.error == null) {
            Text(
                text = if (state.commentsDisabled) {
                    "Nobody is even allowed to make comments. Weird, right?"
                } else {
                    "Nobody has said anything yet. Weird, right?"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        state.items.forEach { comment ->
            CommentRow(
                comment = comment,
                alignedSec = state.aligned[comment.id],
                canReply = state.postingEnabled && state.signedIn,
                replies = state.replies[comment.id],
                loadingReplies = comment.id in state.loadingReplies,
                onToggleReplies = { viewModel.toggleReplies(comment) },
                onOpen = { seconds -> openInYouTube(context, viewModel.videoId, comment.id, seconds) },
                onReply = { viewModel.replyTo(comment) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        if (state.next != null) {
            TextButton(
                onClick = viewModel::loadMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.loading) "Loading…" else "More comments")
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    alignedSec: Long?,
    canReply: Boolean,
    replies: List<Comment>?,
    loadingReplies: Boolean,
    onToggleReplies: () -> Unit,
    onOpen: (Long?) -> Unit,
    onReply: () -> Unit,
) {
    // A comment opens on tap: the words are the content, the icons, likes and replies are the
    // extra detail. Everything below the author stays hidden until the row is opened.
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var overflowing by remember(comment.id) { mutableStateOf(false) }
    Surface(
        color = if (alignedSec != null) {
            // An aligned comment is the one the app has touched; give it a quiet tint.
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp).clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (comment.pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            }
        }

        // Selectable, so a long press can copy what someone actually said. The author and the
        // action row stay outside it: copying a name or a like count is never the point.
        SelectionContainer {
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { overflowing = it.hasVisualOverflow },
            )
        }
        // The reply count is visible before the row is opened: how many replies exist is part
        // of the comment, not of its expansion.
        if (comment.replyCount > 0 && !expanded) {
            Text(
                text = plural(comment.replyCount.toLong(), "reply", "replies"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Every row gets its chevron: a conditional one hid on short comments, which read as
        // "this comment cannot be opened".
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (expanded) "Show less" else "Show the whole comment",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.End)
                .size(24.dp)
                .clickable { expanded = !expanded },
        )

        if (!expanded) return@Column

        // The detail row: likes, age, an aligned timestamp, and the way out to YouTube. A small
        // icon, not the word "Open": the mark says where it goes.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = buildString {
                    if (comment.likeCount > 0) append("${plural(comment.likeCount.toLong(), "like")} · ")
                    append(comment.publishedText)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            mappingChip(comment.timestampSec, alignedSec)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            IconButton(onClick = { onOpen(alignedSec ?: comment.timestampSec) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_youtube),
                    contentDescription = "Open this comment in YouTube",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (comment.replyCount > 0) {
                TextButton(onClick = onToggleReplies) {
                    if (loadingReplies) {
                        Text("Loading…")
                    } else {
                        Icon(
                            imageVector = if (replies == null) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = if (replies == null) "Show replies" else "Hide replies",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(plural(comment.replyCount.toLong(), "reply", "replies"))
                    }
                }
            }
            if (canReply) {
                TextButton(onClick = onReply) { Text("Reply") }
            }
        }

        replies?.forEach { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = reply.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    SelectionContainer {
                        Text(
                            text = reply.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = buildString {
                            if (reply.likeCount > 0) append("${plural(reply.likeCount.toLong(), "like")} · ")
                            append(reply.publishedText)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onOpen(reply.timestampSec) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_youtube),
                        contentDescription = "Open this reply in YouTube",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        }
    }
}

/** "4:09 → 3:58" when Resync moved it; nothing when there is nothing to say. */
private fun mappingChip(original: Long?, aligned: Long?): String? {
    if (aligned == null || aligned == original) return null
    return if (original == null) {
        "aligned ${formatDuration(aligned)}"
    } else {
        "${formatDuration(original)} → ${formatDuration(aligned)}"
    }
}

/**
 * The YouTube app can scroll to a specific comment; a browser cannot. Prefer the app package when
 * it is installed; with no app, open the plain watch URL so the video at least starts at the right
 * timestamp, and say why the comment itself is missing.
 */
private fun openInYouTube(context: Context, videoId: String, commentId: String, seconds: Long?) {
    fun url(withComment: Boolean): String = buildString {
        append("https://www.youtube.com/watch?v=").append(videoId)
        if (withComment) append("&lc=").append(commentId)
        if (seconds != null && seconds > 0) append("&t=").append(seconds).append("s")
    }

    val appOpened = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url(withComment = true)))
                .setPackage("com.google.android.youtube"),
        )
    }.isSuccess
    if (appOpened) return

    val browserOpened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(withComment = false))))
    }.isSuccess
    Toast.makeText(
        context,
        if (browserOpened) {
            "Opening at the timestamp. Browsers cannot jump to a comment."
        } else {
            "No app can open YouTube links"
        },
        Toast.LENGTH_LONG,
    ).show()
}
