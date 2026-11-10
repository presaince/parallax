/*
 * Parallax — Copyright (C) 2026 Bodi
 * GPLv3: see LICENSE. No warranty.
 */
package com.presaince.parallax.feature.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.presaince.parallax.core.AppResult
import com.presaince.parallax.core.SortOption
import com.presaince.parallax.core.SortState
import com.presaince.parallax.data.auth.PostingManager
import com.presaince.parallax.data.library.ContentRepository
import com.presaince.parallax.data.model.Comment
import com.presaince.parallax.data.resync.ResyncEngine
import com.presaince.parallax.data.resync.ResyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentsUiState(
    val items: List<Comment> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** Aligned seconds per comment, when Timestamp Resync has already run for this video. */
    val aligned: Map<String, Long> = emptyMap(),
    /** Replies loaded per parent comment, by comment id. */
    val replies: Map<String, List<Comment>> = emptyMap(),
    val loadingReplies: Set<String> = emptySet(),
    val postingEnabled: Boolean = false,
    val signedIn: Boolean = false,
    val draft: String = "",
    /** The comment being replied to, or null for a new top-level comment. */
    val replyTo: Comment? = null,
    /** The section is collapsed until it is opened: the count is the header. */
    val expanded: Boolean = false,
    val posting: Boolean = false,
    val message: String? = null,
    val resyncReady: Boolean = false,
    val aligning: Boolean = false,
    /** The video's comment total, once a page has reported it. */
    val total: Long? = null,
    /** The video's owner has switched comments off. */
    val commentsDisabled: Boolean = false,
    /** True once the first comments page has settled, success or failure. */
    val firstPageLoaded: Boolean = false,
    /** Newest first until the reader says otherwise. */
    val sort: SortState = SortState(option = SortOption.DATE, ascending = false),
    /** Why replies for one comment did not arrive, shown on that comment. */
    val replyErrors: Map<String, String> = emptyMap(),
)

class CommentsViewModel(
    private val content: ContentRepository,
    private val posting: PostingManager,
    private val resync: ResyncEngine,
    private val resyncStore: ResyncStore,
    val videoId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    init {
        refreshPostingState()
        _state.update { it.copy(resyncReady = resyncStore.load().ready) }
        // Comments load on first expand, not with the player: the streams fetch owns the
        // network at queueing time, and the collapsed header only needs the cached total.
    }

    /** True once the first page has been requested, so collapsing does not refetch. */
    private var firstPageRequested = false

    /** First page in the background, without expanding: the player's load order reaches
     *  comments after chapters, and opening the section should not start the network then. */
    fun preloadFirstPage() {
        if (firstPageRequested) return
        firstPageRequested = true
        loadFirstPage()
    }

    fun loadFirstPage() {
        _state.value = _state.value.copy(
            loading = true,
            items = emptyList(),
            next = null,
            commentsDisabled = false,
        )
        fetch(page = null, append = false)
    }

    fun loadMore() {
        val next = _state.value.next ?: return
        if (_state.value.loading) return
        fetch(page = next, append = true)
    }

    /**
     * The reader's own reload: the cached page is dropped and fetched again even inside its hour.
     * The list stays on screen while it does, so a long press does not blank the section.
     */
    fun refreshComments() {
        if (_state.value.loading) return
        firstPageRequested = true
        _state.update { it.copy(loading = true, error = null, message = null, replies = emptyMap()) }
        fetch(page = null, append = false, refresh = true)
    }

    fun onDraftChange(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun toggleExpanded() {
        val nowExpanded = !_state.value.expanded
        _state.update { it.copy(expanded = nowExpanded) }
        if (nowExpanded && !firstPageRequested) {
            firstPageRequested = true
            loadFirstPage()
            loadAligned()
        }
    }

    fun replyTo(comment: Comment?) {
        _state.update { it.copy(replyTo = comment) }
    }

    /** Tapping the sort already in use flips the direction; picking the other one starts newest. */
    fun setSort(state: SortState) {
        _state.update { it.copy(sort = state) }
    }

    /** Replies load on demand: a page of comments can carry hundreds of them. */
    fun toggleReplies(comment: Comment) {
        val current = _state.value
        if (current.replies.containsKey(comment.id)) {
            _state.update { it.copy(replies = it.replies - comment.id) }
            return
        }
        val page = comment.repliesPage
        if (page.isNullOrBlank()) {
            // Not a silent no-op: say why, and what will fix it (a fresh comment page).
            _state.update {
                it.copy(message = "Replies for ${comment.author} are not loaded yet. Pull the " +
                    "comments again to fetch them.")
            }
            return
        }
        if (comment.id in current.loadingReplies) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    loadingReplies = it.loadingReplies + comment.id,
                    replyErrors = it.replyErrors - comment.id,
                )
            }
            when (val result = content.comments(videoId, page)) {
                is AppResult.Ok -> _state.update {
                    // A reply page that comes back empty is not an error, but it is worth saying:
                    // YouTube counted replies and then handed none over.
                    val empty = result.value.items.isEmpty()
                    it.copy(
                        replies = it.replies + (comment.id to result.value.items),
                        loadingReplies = it.loadingReplies - comment.id,
                        replyErrors = if (empty) {
                            it.replyErrors + (comment.id to "No replies came back for this comment.")
                        } else {
                            it.replyErrors - comment.id
                        },
                        message = if (empty) {
                            "YouTube says ${comment.author} has ${comment.replyCount} replies and " +
                                "sent none of them."
                        } else {
                            it.message
                        },
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(
                        loadingReplies = it.loadingReplies - comment.id,
                        replyErrors = it.replyErrors + (comment.id to result.message),
                    )
                }
            }
        }
    }

    fun post() {
        val current = _state.value
        if (current.posting || current.draft.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(posting = true, message = null) }
            val result = if (current.replyTo != null) {
                posting.reply(videoId, current.replyTo.id, current.draft.trim())
            } else {
                posting.post(videoId, current.draft.trim())
            }
            when (result) {
                is AppResult.Ok -> {
                    _state.update {
                        it.copy(posting = false, draft = "", replyTo = null, message = "Posted")
                    }
                    loadFirstPage()
                }
                is AppResult.Err -> _state.update {
                    it.copy(posting = false, message = result.message)
                }
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Resync lives with the comments it adjusts: the toggle is in Settings, the action is here, and
     * the result appears on the comment it belongs to.
     */
    fun align() {
        val comments = _state.value.items
        if (comments.isEmpty() || _state.value.aligning) return
        viewModelScope.launch {
            _state.update { it.copy(aligning = true, message = null) }
            val settings = resyncStore.load()
            _state.update { it.copy(resyncReady = settings.ready) }
            when (val result = resync.align(videoId, comments)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        aligning = false,
                        aligned = result.value.moments.associate { m -> m.commentId to m.seconds },
                        message = "${result.value.moments.size} aligned",
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(aligning = false, message = result.message)
                }
            }
        }
    }

    fun refreshPostingState() {
        val settings = posting.settings()
        _state.update {
            it.copy(postingEnabled = settings.enabled, signedIn = settings.signedIn)
        }
    }

    private fun loadAligned() {
        viewModelScope.launch {
            val moments = resync.stored(videoId)
            if (moments.isNotEmpty()) {
                _state.update { it.copy(aligned = moments.associate { m -> m.commentId to m.seconds }) }
            }
        }
    }

    private fun fetch(page: String?, append: Boolean, refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = content.comments(videoId, page, refresh)) {
                is AppResult.Ok -> _state.update {
                    it.copy(
                        items = if (append) it.items + result.value.items else result.value.items,
                        next = result.value.next,
                        loading = false,
                        total = result.value.total ?: it.total,
                        commentsDisabled = if (append) it.commentsDisabled else result.value.disabled,
                        firstPageLoaded = if (page == null && !append) true else it.firstPageLoaded,
                    )
                }
                is AppResult.Err -> _state.update {
                    it.copy(
                        loading = false,
                        error = result.message,
                        firstPageLoaded = if (page == null && !append) true else it.firstPageLoaded,
                    )
                }
            }
        }
    }
}
