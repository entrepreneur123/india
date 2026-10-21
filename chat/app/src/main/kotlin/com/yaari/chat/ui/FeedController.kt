package com.yaari.chat.ui

import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.data.BackendException
import com.yaari.chat.model.Comment
import com.yaari.chat.model.FeedTab
import com.yaari.chat.model.Post
import com.yaari.chat.model.PostVisibility
import com.yaari.chat.model.ReportReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One of the feed's lists: null until loaded; [end] once a page came back short (nothing older). */
data class FeedPage(
    val posts: List<Post>? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val end: Boolean = false,
)

/** The comments of [post], oldest first ([comments] null until loaded); [replyTo]: the comment my next one answers. */
data class CommentThread(
    val post: Post,
    val comments: List<Comment>? = null,
    val failed: Boolean = false,
    val more: Boolean = false,
    val sending: Boolean = false,
    val replyTo: Comment? = null,
)

/**
 * The feed's state. [detail]: one post open full screen (from a profile's grid or Notifications); [thread]: the
 * comments sheet; [hidden]: posts I reported (gone from my lists); [opening]: a post being fetched by its id.
 */
data class FeedState(
    val tab: FeedTab = FeedTab.FRIENDS,
    val pages: Map<FeedTab, FeedPage> = emptyMap(),
    val detail: Post? = null,
    val thread: CommentThread? = null,
    val hidden: Set<String> = emptySet(),
    val opening: Boolean = false,
) {
    fun page(t: FeedTab = tab) = pages[t] ?: FeedPage()
}

/**
 * The feed (Friends / Discover), hearts and comments. Owned by [AppViewModel]; plain coroutines on its scope.
 * Hearts are optimistic: the count changes at once and the server's answer settles it (a failure puts it back).
 * [onPost]: a post changed (its counts), so my profile and the open profile follow; [onRemoved]: it is gone.
 */
class FeedController(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val notice: (UiText) -> Unit,
    private val explain: (Throwable) -> UiText,
    private val onPost: (String, (Post) -> Post) -> Unit,
    private val onRemoved: (String) -> Unit,
) {
    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val loads = mutableMapOf<FeedTab, Job>()
    private var commentsJob: Job? = null
    /** The newest heart tap per post: an older answer arriving later doesn't overwrite it. */
    private val likeTaps = mutableMapOf<String, Int>()

    fun reset() {
        loads.values.forEach { it.cancel() }
        loads.clear()
        commentsJob?.cancel()
        likeTaps.clear()
        _state.value = FeedState()
    }

    // ---- the lists ----

    /** The Feed tab came on screen: the chosen list loads (again: what is shown stays until the new page is in). */
    fun opened() = refresh(_state.value.tab)

    fun select(tab: FeedTab) {
        _state.update { it.copy(tab = tab) }
        if (!_state.value.page(tab).loading) refresh(tab)
    }

    fun refresh(tab: FeedTab = _state.value.tab) = load(tab, more = false)

    /** The next page, when the list is scrolled near its end. */
    fun loadMore(tab: FeedTab = _state.value.tab) {
        val p = _state.value.page(tab)
        if (p.loading || p.end || p.posts.isNullOrEmpty() || p.failed) return
        load(tab, more = true)
    }

    private fun load(tab: FeedTab, more: Boolean) {
        if (more && loads[tab]?.isActive == true) return
        loads[tab]?.cancel()
        setPage(tab) { it.copy(loading = true, failed = false) }
        loads[tab] = scope.launch {
            try {
                val before = if (more) _state.value.page(tab).posts?.lastOrNull() else null
                val hidden = _state.value.hidden
                val all = backend.feed(tab, before)
                val got = all.filterNot { it.id in hidden }
                setPage(tab) { p ->
                    val list = if (more) (p.posts.orEmpty() + got).distinctBy { it.id } else got
                    p.copy(posts = list, loading = false, failed = false, end = all.size < PAGE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                setPage(tab) { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun setPage(tab: FeedTab, change: (FeedPage) -> FeedPage) =
        _state.update { s -> s.copy(pages = s.pages + (tab to change(s.page(tab)))) }

    /** What [AppViewModel.addPost] just shared: on top of Friends at once (and of Discover when it is public). */
    fun added(post: Post) {
        _state.update { s ->
            val tabs = if (post.visibility == PostVisibility.PUBLIC) FeedTab.entries else listOf(FeedTab.FRIENDS)
            s.copy(pages = s.pages.mapValues { (t, p) -> if (t in tabs && p.posts != null) p.copy(posts = listOf(post) + p.posts) else p })
        }
    }

    /** Someone blocked or reported: their posts and comments leave what is on screen. */
    fun forget(userId: String) {
        _state.update { s ->
            s.copy(
                pages = s.pages.mapValues { (_, p) -> p.copy(posts = p.posts?.filterNot { it.author?.id == userId }) },
                detail = s.detail?.takeIf { it.author?.id != userId },
                thread = s.thread?.let { t ->
                    if (t.post.author?.id == userId) null else t.copy(comments = t.comments?.filterNot { it.author.id == userId })
                },
            )
        }
    }

    /** Changes the post wherever it is shown: the lists, the open post, the comments sheet, and (through [onPost]) profiles. */
    private fun patch(postId: String, change: (Post) -> Post) {
        _state.update { s ->
            s.copy(
                pages = s.pages.mapValues { (_, p) -> p.copy(posts = p.posts?.map { if (it.id == postId) change(it) else it }) },
                detail = s.detail?.let { if (it.id == postId) change(it) else it },
                thread = s.thread?.let { t -> if (t.post.id == postId) t.copy(post = change(t.post)) else t },
            )
        }
        onPost(postId, change)
    }

    private fun drop(postId: String) {
        _state.update { s ->
            s.copy(
                pages = s.pages.mapValues { (_, p) -> p.copy(posts = p.posts?.filterNot { it.id == postId }) },
                detail = s.detail?.takeIf { it.id != postId },
                thread = s.thread?.takeIf { it.post.id != postId },
            )
        }
        onRemoved(postId)
    }

    // ---- hearts ----

    /** Toggles my heart at once; the server's count settles it, a failure puts it back. */
    fun toggleLike(post: Post) = setLike(post, !current(post).liked)

    /** Double tap on the picture: only ever likes (as Instagram). */
    fun like(post: Post) {
        if (!current(post).liked) setLike(post, true)
    }

    private fun current(post: Post): Post {
        val s = _state.value
        return s.pages.values.firstNotNullOfOrNull { p -> p.posts?.firstOrNull { it.id == post.id } }
            ?: s.detail?.takeIf { it.id == post.id } ?: s.thread?.post?.takeIf { it.id == post.id } ?: post
    }

    private fun setLike(post: Post, on: Boolean) {
        val before = current(post)
        val tap = (likeTaps[post.id] ?: 0) + 1
        likeTaps[post.id] = tap
        patch(post.id) { it.copy(liked = on, likes = (it.likes + if (on) 1 else -1).coerceAtLeast(0)) }
        scope.launch {
            try {
                val r = backend.likePost(post.id, on)
                if (likeTaps[post.id] == tap) patch(post.id) { it.copy(liked = r.liked, likes = r.likes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (likeTaps[post.id] == tap) patch(post.id) { it.copy(liked = before.liked, likes = before.likes) }
                if ((e as? BackendException)?.code == "not_found") drop(post.id)
                notice(explain(e))
            }
        }
    }

    // ---- one post ----

    /** Opens [post] full screen (a profile's grid). */
    fun openDetail(post: Post) = _state.update { it.copy(detail = current(post).let { c -> if (c.author == null) post else c }) }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    /** A post by its id (Notifications): full screen, with its comments open. Gone posts say so. */
    fun openPost(postId: String) {
        if (_state.value.opening) return
        _state.update { it.copy(opening = true) }
        scope.launch {
            try {
                val post = backend.post(postId) ?: return@launch notice(UiText(R.string.err_not_found))
                _state.update { it.copy(detail = post) }
                openComments(post)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            } finally {
                _state.update { it.copy(opening = false) }
            }
        }
    }

    /** Mine only; it leaves the lists, my profile and anything open. */
    fun delete(post: Post) {
        scope.launch {
            try {
                backend.deletePost(post.id)
                drop(post.id)
                notice(UiText(R.string.msg_post_deleted))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    /** Sent to the safety team; the post leaves my lists. */
    fun report(post: Post, reason: ReportReason) {
        scope.launch {
            try {
                backend.reportPost(post.id, reason)
                _state.update { it.copy(hidden = it.hidden + post.id) }
                drop(post.id)
                notice(UiText(R.string.msg_post_reported))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    // ---- comments ----

    fun openComments(post: Post) {
        _state.update { it.copy(thread = CommentThread(current(post))) }
        loadComments()
    }

    fun closeComments() {
        commentsJob?.cancel()
        _state.update { it.copy(thread = null) }
    }

    fun loadComments(more: Boolean = false) {
        val t = _state.value.thread ?: return
        commentsJob?.cancel()
        _state.update { s -> s.copy(thread = s.thread?.copy(failed = false)) }
        commentsJob = scope.launch {
            try {
                val after = if (more) t.comments?.lastOrNull()?.id else null
                val got = backend.comments(t.post.id, after)
                updateThread(t.post.id) { th ->
                    val list = if (more) (th.comments.orEmpty() + got).distinctBy { it.id } else got
                    th.copy(comments = list, failed = false, more = got.size >= COMMENTS_PAGE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateThread(t.post.id) { it.copy(failed = true) }
                if ((e as? BackendException)?.code == "not_found") {
                    drop(t.post.id)
                    notice(explain(e))
                }
            }
        }
    }

    fun replyTo(c: Comment?) = _state.update { s -> s.copy(thread = s.thread?.copy(replyTo = c)) }

    /** Sends my comment (1–300 characters); [sent] clears the field once it is in. */
    fun send(text: String, sent: () -> Unit = {}) {
        val t = _state.value.thread ?: return
        val body = text.trim()
        if (body.isEmpty() || t.sending) return
        if (body.length > Comment.MAX) return notice(UiText(R.string.comment_too_long, Comment.MAX))
        updateThread(t.post.id) { it.copy(sending = true) }
        scope.launch {
            try {
                val c = backend.addComment(t.post.id, body, t.replyTo?.id)
                updateThread(t.post.id) { th -> th.copy(comments = th.comments.orEmpty() + c, replyTo = null) }
                patch(t.post.id) { it.copy(comments = it.comments + 1) }
                sent()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(if ((e as? BackendException)?.code == "bad_request") UiText(R.string.comment_too_long, Comment.MAX) else explain(e))
            } finally {
                updateThread(t.post.id) { it.copy(sending = false) }
            }
        }
    }

    fun remove(c: Comment) {
        scope.launch {
            try {
                backend.removeComment(c.id)
                updateThread(c.postId) { th -> th.copy(comments = th.comments?.filterNot { it.id == c.id }, replyTo = th.replyTo?.takeIf { it.id != c.id }) }
                patch(c.postId) { it.copy(comments = (it.comments - 1).coerceAtLeast(0)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    fun report(c: Comment, reason: ReportReason) {
        scope.launch {
            try {
                backend.reportComment(c.id, reason)
                updateThread(c.postId) { th -> th.copy(comments = th.comments?.filterNot { it.id == c.id }) }
                notice(UiText(R.string.msg_comment_reported))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice(explain(e))
            }
        }
    }

    private fun updateThread(postId: String, change: (CommentThread) -> CommentThread) =
        _state.update { s -> s.copy(thread = s.thread?.let { if (it.post.id == postId) change(it) else it }) }

    companion object {
        /** The server's page sizes (feed / comments_list). */
        const val PAGE = 20
        const val COMMENTS_PAGE = 50
    }
}
