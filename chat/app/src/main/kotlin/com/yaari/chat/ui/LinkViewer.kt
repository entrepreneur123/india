package com.yaari.chat.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.TextView
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.PresentToAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopScreenShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.yaari.chat.R
import com.yaari.chat.data.Backend
import com.yaari.chat.model.LinkPreview
import com.yaari.chat.model.Links
import com.yaari.chat.model.RoomStage
import com.yaari.chat.model.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

// ---- Links in chats: tappable addresses, a small card per message, and the in-app viewer -------------------------
// Only http(s) addresses are links. A tap opens them in the viewer (a hardened WebView: no file or content access, no
// JavaScript bridge, other schemes handed to the phone through a chooser and never loaded). A room's host or co-host
// can show the page to everyone (036: voice_share_link), which pins it beside the room's chat.

/**
 * Link cards (edge function link-meta), remembered per address for as long as the app runs (at most [MAX]). One
 * request per address, shared by every bubble that shows it; a failed one is remembered too (no retry storm).
 */
class LinkCards(private val backend: Backend, private val scope: CoroutineScope) {
    private val known = object : LinkedHashMap<String, LinkPreview?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LinkPreview?>?) = size > MAX
    }
    private val loading = HashMap<String, Deferred<LinkPreview?>>()

    /** Already looked up (null inside: there is no card for it). Main thread only, as [get]. */
    fun cached(url: String): Pair<Boolean, LinkPreview?> = if (known.containsKey(url)) true to known[url] else false to null

    /** The card for [url]; looked up once (the lookup outlives a bubble scrolled away, so it is not lost). */
    suspend fun get(url: String): LinkPreview? {
        if (known.containsKey(url)) return known[url]
        val job = loading.getOrPut(url) {
            scope.async { runCatching { backend.linkPreview(url) }.getOrNull().also { known[url] = it } }
        }
        return try {
            job.await()
        } finally {
            if (job.isCompleted) loading.remove(url, job)
        }
    }

    private companion object {
        const val MAX = 200
    }
}

/** A page opened in the viewer; [share]: a room's host or co-host may show it to everyone (with the page's title). */
data class OpenLink(val url: String, val share: ((url: String, title: String?) -> Unit)? = null)

/** What chats need to show links: the cards, and the viewer to open them in. Screenshot tests get [None]. */
@Stable
class LinkActions(val cards: LinkCards?) {
    var shown by mutableStateOf<OpenLink?>(null)
        private set

    fun open(url: String, share: ((String, String?) -> Unit)? = null) {
        if (Links.isWeb(url)) shown = OpenLink(url, share)
    }

    fun close() {
        shown = null
    }

    companion object {
        val None = LinkActions(null)
    }
}

val LocalLinks = staticCompositionLocalOf { LinkActions.None }

/** [text] with its http(s) addresses underlined in [color] and tappable ([onLink]). Text without links is returned as it is. */
fun linkify(text: AnnotatedString, color: Color, onLink: (String) -> Unit): AnnotatedString {
    val found = Links.find(text.text)
    if (found.isEmpty()) return text
    return buildAnnotatedString {
        append(text)
        val style = TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline))
        found.forEach { l -> addLink(LinkAnnotation.Clickable(l.url, style) { onLink(l.url) }, l.start, l.end) }
    }
}

/** A message's links, styled and tappable: the viewer for web pages, the phone's YouTube app or browser for videos. */
@Composable
fun rememberLinked(text: AnnotatedString, color: Color, onLink: ((String) -> Unit)? = null): AnnotatedString {
    val links = LocalLinks.current
    val context = LocalContext.current
    val tap by rememberUpdatedState(onLink ?: { url: String -> openLink(context, links, url) })
    return remember(text, color) { linkify(text, color) { tap(it) } }
}

/** A tapped link: YouTube videos go to the phone (as before), every other page to the in-app viewer. */
fun openLink(context: Context, links: LinkActions, url: String) {
    if (YouTube.videoId(url) != null) openOutside(context, url) else links.open(url)
}

/**
 * Opens an http(s) [url] outside the app (the phone's browser or the app that handles it), as a browsable link only.
 * Nothing else is ever opened this way.
 */
fun openOutside(context: Context, url: String) {
    if (!Links.isWeb(url)) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Unit
    } catch (_: SecurityException) {
        Unit
    }
}

/** Schemes a page may hand to another app (after the person tapped, through a chooser). Everything else is ignored. */
private val HANDED_OFF = setOf("tel", "mailto", "sms", "smsto", "geo", "market", "upi", "whatsapp")

/**
 * A page's link to something that isn't a web page: never loaded in the viewer. Known schemes (tel:, market:, ...) and
 * intent: links (stripped to a plain browsable VIEW: no component, no selector, no package) go to the phone through a
 * chooser; an intent: link's https fallback opens in the viewer instead. Returns the https page to load, if any.
 */
internal fun handOff(context: Context, uri: Uri): String? {
    val scheme = uri.scheme?.lowercase() ?: return null
    val intent = when (scheme) {
        "intent" -> {
            val parsed = runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return null
            parsed.getStringExtra("browser_fallback_url")?.takeIf(Links::isWeb)?.let { return it }
            val data = parsed.data ?: return null
            if (data.scheme?.lowercase() !in HANDED_OFF && !Links.isWeb(data.toString())) return null
            Intent(Intent.ACTION_VIEW, data)
        }
        in HANDED_OFF -> Intent(Intent.ACTION_VIEW, uri)
        else -> return null
    }
    intent.addCategory(Intent.CATEGORY_BROWSABLE)
    val chooser = Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Unit
    } catch (_: SecurityException) {
        Unit
    }
    return null
}

/** One page shown in a [SafeWebView]: its title and progress as it loads, and what the toolbar asks of it. */
@Stable
class WebPage {
    var title by mutableStateOf<String?>(null)
        internal set
    var progress by mutableFloatStateOf(0f)
        internal set
    internal var view: WebView? = null

    fun reload() {
        view?.reload()
    }
}

/**
 * A WebView that can only show web pages: JavaScript on (most pages need it), but no file or content access, no
 * JavaScript bridge, no pop-up windows, no geolocation, camera or microphone, no file uploads, no mixed content, and
 * Safe Browsing on. Only http(s) loads inside; other links go through [handOff] (and only when tapped). Destroyed when
 * it leaves the screen. [url] changing loads the new page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafeWebView(url: String, page: WebPage, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    key(url) {
        AndroidView<View>(
            factory = { ctx ->
                // A phone whose WebView is missing or being updated can't make one: the toolbar's "Open in browser" stays.
                runCatching { WebView(ctx) }.getOrElse { page.progress = 1f; return@AndroidView TextView(ctx) }.apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        setGeolocationEnabled(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        mediaPlaybackRequiresUserGesture = true
                        @Suppress("DEPRECATION") saveFormData = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching { safeBrowsingEnabled = true }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url
                            if (Links.isWeb(target.toString())) return false // a web page: stays in the viewer
                            // Anything else is never loaded here; handed to the phone only when the person tapped it.
                            if (request.isForMainFrame && request.hasGesture()) handOff(context, target)?.let { view.loadUrl(it) }
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            page.progress = 0.1f
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            page.progress = 1f
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView, title: String?) {
                            page.title = title?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("http") }
                        }

                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            page.progress = newProgress / 100f
                        }

                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

                        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) =
                            callback.invoke(origin, false, false)

                        override fun onShowFileChooser(
                            webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams,
                        ): Boolean = false
                    }
                    setDownloadListener { _, _, _, _, _ -> Unit } // no downloads from the viewer
                    page.view = this
                    page.title = null
                    page.progress = 0f
                    if (Links.isWeb(url)) loadUrl(url)
                }
            },
            modifier = modifier,
            onRelease = { view ->
                if (view is WebView) {
                    if (page.view === view) page.view = null
                    view.stopLoading()
                    view.setDownloadListener(null)
                    view.webChromeClient = null
                    view.destroy()
                }
            },
        )
    }
}

/**
 * The in-app viewer, over everything while a link is open: the page with a toolbar (close, the title and site,
 * reload, open in the browser), and for a room's host or co-host "Show to everyone".
 */
@Composable
fun LinkViewerHost(links: LinkActions) {
    val open = links.shown ?: return
    val context = LocalContext.current
    val page = remember(open.url) { WebPage() }
    Dialog(onDismissRequest = links::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val title = page.title ?: Links.host(open.url)
        val pane = stringResource(R.string.link_viewer)
        Column(
            Modifier.fillMaxSize().padding(top = 24.dp).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface).semantics { paneTitle = pane },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Rounded.Close, stringResource(R.string.close), links::close)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Links.host(open.url), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                CircleIconButton(Icons.Rounded.Refresh, stringResource(R.string.link_reload), page::reload)
                CircleIconButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.link_open_browser), { openOutside(context, open.url) })
            }
            val share = open.share
            if (share != null && Links.shareable(open.url)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.link_share_hint), Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    )
                    SoftButton(
                        stringResource(R.string.link_share), { share(open.url, page.title); links.close() }, accent = true, height = 36.dp,
                        leading = { Icon(Icons.Outlined.PresentToAll, null, Modifier.size(18.dp), tint = Color.White) },
                    )
                }
            }
            if (page.progress < 1f) {
                LinearProgressIndicator({ page.progress.coerceIn(0.05f, 1f) }, Modifier.fillMaxWidth().height(2.dp), color = Brand.Pink)
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            SafeWebView(open.url, page, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/**
 * The page a host or co-host shared with the room (036), beside the chat: who shared it, the page itself, and the
 * tools: reload, open in the browser, close (on my phone only), and for the host or a co-host, stop sharing.
 */
@Composable
fun SharedPagePanel(stage: RoomStage, canStop: Boolean, onClose: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val page = remember(stage.url, stage.at) { WebPage() }
    val shape = RoundedCornerShape(16.dp)
    val pane = stringResource(R.string.link_shared_by, stage.by)
    Column(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).semantics { paneTitle = pane },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.LINK, size = 18.dp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    pane, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    page.title ?: stage.title ?: Links.host(stage.url), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            CircleIconButton(Icons.Rounded.Refresh, stringResource(R.string.link_reload), page::reload, size = 36.dp, iconSize = 20.dp)
            CircleIconButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.link_open_browser), { openOutside(context, stage.url) }, size = 36.dp, iconSize = 20.dp)
            if (canStop) {
                CircleIconButton(
                    Icons.Rounded.StopScreenShare, stringResource(R.string.link_stop_sharing), onStop, size = 36.dp, iconSize = 20.dp,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            CircleIconButton(Icons.Rounded.Close, stringResource(R.string.link_close_for_me), onClose, size = 36.dp, iconSize = 20.dp)
        }
        if (page.progress < 1f) {
            LinearProgressIndicator({ page.progress.coerceIn(0.05f, 1f) }, Modifier.fillMaxWidth().height(2.dp), color = Brand.Pink)
        }
        if (LocalYouTubePlayer.current) {
            SafeWebView(stage.url, page, Modifier.fillMaxWidth().weight(1f).background(Color.White))
        } else {
            // Screenshot tests: no WebView, the page's address in its place.
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Text(Links.host(stage.url), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The card under a message with a link: a YouTube video's thumbnail (a tap opens it on the phone; in a room, [onRequest]
 * adds it to Up next), or a web page's site, title, description and image from link-meta (a tap opens the viewer).
 */
@Composable
fun MessageLinkCard(text: String, modifier: Modifier = Modifier, onRequest: ((String) -> Unit)? = null, onLink: ((String) -> Unit)? = null) {
    val links = LocalLinks.current
    val context = LocalContext.current
    val found = remember(text) { Links.find(text) }
    if (found.isEmpty()) return
    val video = remember(found) { found.firstNotNullOfOrNull { YouTube.videoId(it.url) } }
    val page = remember(found) { found.firstOrNull { YouTube.videoId(it.url) == null }?.url }
    when {
        page != null -> PageCard(page, links, modifier) { (onLink ?: { u: String -> links.open(u) })(page) }
        video != null -> VideoCard(video, modifier, onRequest) { openOutside(context, "https://youtu.be/$video") }
    }
}

@Composable
private fun PageCard(url: String, links: LinkActions, modifier: Modifier, onOpen: () -> Unit) {
    val cards = links.cards
    val preview by produceState(cards?.cached(url)?.second, url, cards) {
        if (cards != null) value = cards.get(url)
    }
    val p = preview
    val host = p?.site ?: Links.host(url)
    val title = p?.title
    val label = stringResource(R.string.link_open, title ?: host)
    Row(
        modifier.width(260.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.14f))
            .clickable(role = Role.Button, onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = label }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.LINK, size = 24.dp)
            p?.image?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(host, color = chatColors().meta, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = Jakarta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                title ?: url, color = chatColors().text, fontSize = 14.sp, lineHeight = 18.sp, fontFamily = Jakarta,
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
            )
            p?.description?.let {
                Text(it, color = chatColors().meta, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = Jakarta, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VideoCard(videoId: String, modifier: Modifier, onRequest: ((String) -> Unit)?, onOpen: () -> Unit) {
    val label = stringResource(R.string.link_open_video)
    Column(modifier.width(260.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.14f))) {
        Box(
            Modifier.fillMaxWidth().height(146.dp).background(Color.Black).clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(YouTube.thumbnail(videoId), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp), tint = Color.White)
            }
        }
        if (onRequest != null) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button) { onRequest(videoId) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TopicIcon(Topic.MUSIC, size = 18.dp)
                Text(stringResource(R.string.link_request_song), color = chatColors().text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
