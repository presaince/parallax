/*
 * GlassTube — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.glasstube.feature.comments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.presaince.glasstube.R
import com.presaince.glasstube.core.SortRow
import com.presaince.glasstube.core.formatDuration
import com.presaince.glasstube.core.plural
import com.presaince.glasstube.data.model.Comment
import com.presaince.glasstube.ui.component.LinkifiedText
import com.presaince.glasstube.ui.theme.accentTextColour

/**
 * Comments render inside the player page, not on a screen of their own: reading what people said
 * about the second you are watching should not mean leaving the video.
 *
 * They are *items* of that page's list rather than one tall block. A block looked fine until the
 * reader asked for more comments: pages are sorted as they arrive, so a page could insert rows
 * above the one being read, and a non-lazy block has no key to hold its place. Items are keyed by
 * comment id, so Compose keeps whatever is on screen where it was.
 *
 * Posting appears only when the user has turned it on and signed in, so a viewer never sees a
 * compose box they cannot use.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.commentsItems(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    labelsRight: Boolean = false,
) {
    item(key = "comments-header") {
        CommentsHeader(state = state, viewModel = viewModel, labelsRight = labelsRight)
    }
    if (!state.expanded) return

    item(key = "comments-tools") {
        CommentsTools(state = state, viewModel = viewModel)
    }

    val sorted = sortedComments(state.items, state.sort)
    if (sorted.isNotEmpty()) {
        item(key = "comments-sort") {
            // The order control sits where the order applies: above the first comment, on the
            // left. Tapping the option already in use flips the direction.
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SortRow(
                    state = state.sort,
                    onState = viewModel::setSort,
                    options = COMMENT_SORTS,
                )
            }
        }
    }

    items(sorted, key = { "comment-${it.id}" }) { comment ->
        val context = LocalContext.current
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            CommentRow(
                comment = comment,
                alignedSec = state.aligned[comment.id],
                canReply = state.postingEnabled && state.signedIn,
                replies = state.replies[comment.id],
                loadingReplies = comment.id in state.loadingReplies,
                replyError = state.replyErrors[comment.id],
                onToggleReplies = { viewModel.toggleReplies(comment) },
                onOpen = { seconds -> openInYouTube(context, viewModel.videoId, comment.id, seconds) },
                onReply = { viewModel.replyTo(comment) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }

    if (state.next != null) {
        item(key = "comments-more") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(
                    onClick = viewModel::loadMore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.loading) "Loading…" else "More comments")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentsHeader(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    labelsRight: Boolean,
) {
    // The header opens and closes the section, like a description: the count is the label.
    // A long press reloads: a cached page inside its hour used to be the end of the road.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 44.dp)
            .combinedClickable(
                onClickLabel = if (state.expanded) "Collapse comments" else "Expand comments",
                onLongClickLabel = "Refresh comments",
                onLongClick = viewModel::refreshComments,
                onClick = viewModel::toggleExpanded,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (labelsRight) Spacer(modifier = Modifier.weight(1f))
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = accentTextColour(),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            // A count with no comments under it is a promise the section cannot keep. Show the
            // total only once there is a page to show it with.
            text = state.total
                ?.takeIf { it > 0 && (state.items.isNotEmpty() || state.loading) }
                ?.let { "Comments ($it)" }
                ?: "Comments",
            style = MaterialTheme.typography.titleSmall,
            color = accentTextColour(),
        )
        if (!labelsRight) Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (state.expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = if (state.expanded) "Collapse comments" else "Expand comments",
            tint = accentTextColour(),
        )
    }
}

/** Everything between the header and the first comment: aligning, the compose box, the notices. */
@Composable
private fun CommentsTools(state: CommentsUiState, viewModel: CommentsViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    alignedSec: Long?,
    canReply: Boolean,
    replies: List<Comment>?,
    loadingReplies: Boolean,
    replyError: String?,
    onToggleReplies: () -> Unit,
    onOpen: (Long?) -> Unit,
    onReply: () -> Unit,
) {
    // A comment opens on tap when there is more to see: replies to read, a reply to write, or
    // words running past the six lines. A short comment with none of those has nothing to expand
    // into, so it does not pretend to.
    var expanded by remember(comment.id) { mutableStateOf(false) }
    var overflowing by remember(comment.id) { mutableStateOf(false) }
    val expandable = comment.replyCount > 0 || canReply || overflowing
    val context = LocalContext.current
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
        Column(
            modifier = Modifier
                .padding(10.dp)
                .clickable(enabled = expandable) { expanded = !expanded },
        ) {
        // The name sits on the right, in the same column as the chevron: the words are what the
        // reader scans, and the author is the label on the way in. Tapping the name opens the
        // commenter's channel; the app has no profile screen, so it leaves for the browser.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (comment.pinned) {
                Icon(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            }
            Text(
                text = comment.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (comment.authorUrl != null) {
                    Modifier.clickable { openInBrowser(context, comment.authorUrl) }
                } else {
                    Modifier
                },
            )
        }

        // Selectable, so a long press can copy what someone actually said. The author and the
        // action row stay outside it: copying a name or a like count is never the point. A tap on
        // a link opens it; a tap anywhere else still opens the comment, as the card does.
        SelectionContainer {
            LinkifiedText(
                text = comment.text,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                style = MaterialTheme.typography.bodyMedium,
                colour = MaterialTheme.colorScheme.onSurface,
                onTapElsewhere = if (expandable) ({ expanded = !expanded }) else null,
                // Only while folded: once open, the chevron has to stay so the card can close
                // again, and "still overflowing" is what keeps it there.
                onOverflow = { if (!expanded) overflowing = it },
            )
        }

        // Bottom left is where a control lives, and the chevron belongs to the comments that need
        // one: words running past the six lines. Replies sit beside it, likes and age on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (overflowing) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Show less" else "Show the whole comment",
                    tint = accentTextColour(),
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clickable { expanded = !expanded },
                )
            }
            if (comment.replyCount > 0) {
                // A comment whose replies came without a token can only be answered by the
                // YouTube app, so its control says so and goes there.
                val inPage = !comment.repliesPage.isNullOrBlank()
                Row(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clickable {
                            if (inPage) onToggleReplies() else onOpen(comment.timestampSec)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            loadingReplies -> "Loading…"
                            inPage -> plural(comment.replyCount.toLong(), "reply", "replies")
                            else -> "${plural(comment.replyCount.toLong(), "reply", "replies")} · YouTube"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentTextColour(),
                    )
                    if (inPage) {
                        Icon(
                            imageVector = if (replies == null) {
                                Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowUp
                            },
                            contentDescription = if (replies == null) "Show replies" else "Hide replies",
                            tint = accentTextColour(),
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_new),
                            contentDescription = "These replies open in YouTube",
                            tint = accentTextColour(),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = buildString {
                    if (comment.likeCount > 0) append("${plural(comment.likeCount.toLong(), "like")} · ")
                    append(comment.publishedText)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Why a reply page did not arrive, on the card that asked for it rather than at the top
        // of a section the reader is far below.
        replyError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (!expanded) {
            // Replies can be open while the words are still folded: the footer opened them.
            replies?.forEach { reply -> ReplyRow(reply) }
            return@Column
        }

        // The detail row: the aligned timestamp and the reply action. Likes and age have already
        // been said on the footer line, and the YouTube mark is gone from comments entirely.
        if (mappingChip(comment.timestampSec, alignedSec) != null || canReply) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                mappingChip(comment.timestampSec, alignedSec)?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                if (canReply) {
                    TextButton(onClick = onReply) { Text("Reply") }
                }
            }
        }

        replies?.forEach { reply -> ReplyRow(reply) }
        }
    }
}

/** One reply: same shape as a comment, one step quieter. */
@Composable
private fun ReplyRow(reply: Comment) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp)) {
        // The name is on the right here too, so a thread reads as one column of words, and it is
        // the reply's own control: the card's expand click must not swallow it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
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
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (reply.authorUrl != null) {
                    Modifier.clickable { openInBrowser(context, reply.authorUrl) }
                } else {
                    Modifier
                },
            )
        }
        SelectionContainer {
            LinkifiedText(
                text = reply.text,
                // A reply is short by nature; truncating one hides the sentence that answered.
                maxLines = Int.MAX_VALUE,
                style = MaterialTheme.typography.bodySmall,
                colour = MaterialTheme.colorScheme.onSurface,
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
 * The app has no profile screen, so a commenter's name leaves for the browser. A phone with
 * nothing able to open an https link says so rather than swallowing the tap.
 */
private fun openInBrowser(context: Context, url: String) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.isSuccess
    if (!opened) Toast.makeText(context, "No app can open that link", Toast.LENGTH_SHORT).show()
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
