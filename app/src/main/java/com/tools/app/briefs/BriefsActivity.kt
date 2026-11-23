package com.tools.app.briefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tools.app.reader.ReaderBar
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiColors
import com.tools.app.ui.theme.OneUiTheme
import com.tools.app.ui.theme.SamsungFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Briefs, opened from its own launcher icon or from Home; it lives in its own task like the Document reader. */
class BriefsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Briefs.load(this)
        setContent { OneUiTheme { BriefsRoot(onClose = ::finish) } }
    }

    override fun onStart() {
        super.onStart()
        // The only time Briefs syncs: when you open it (at most once a minute).
        if (BriefsAccount.get(this) != null && System.currentTimeMillis() - Briefs.lastSync > 60_000) Briefs.sync(this)
    }
}

@Composable
private fun BriefsRoot(onClose: () -> Unit) {
    val context = LocalContext.current
    var hasAccount by remember { mutableStateOf(BriefsAccount.get(context) != null) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    BackHandler(enabled = open != null) { open = null }
    AnimatedContent(
        open,
        transitionSpec = {
            if (targetState != null) (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
            else fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(200)))
        },
        label = "briefs",
    ) { id ->
        when {
            id != null -> ArticleScreen(id, onBack = { open = null }, onOpen = { open = it })
            hasAccount -> BriefList(onOpen = { open = it }, onBack = onClose, onSignedOut = { hasAccount = false })
            else -> Setup(onBack = onClose, onDone = { hasAccount = true; Briefs.sync(context) })
        }
    }
}

// ---------------------------------------------------------------- setup

@Composable
private fun Setup(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ready = email.contains('@') && password.isNotBlank() && label.isNotBlank() && !busy
    fun connect() {
        if (!ready) return
        val a = Account(email.trim(), password.replace(" ", ""), label.trim())
        busy = true; error = null
        scope.launch {
            val e = Briefs.test(a)
            busy = false
            if (e == null) { BriefsAccount.save(context, a); onDone() } else error = e
        }
    }
    OneUiScaffold(title = "Briefs", onBack = onBack) {
        description("Your newsletters and articles from one Gmail label, as clean, readable pages. Briefs only syncs while it's open: nothing runs in the background and there are no notifications.")
        card("form") {
            Column(Modifier.padding(16.dp)) {
                Field(email, { email = it }, "Gmail address", keyboard = KeyboardType.Email)
                Field(password, { password = it }, "App password", password = true, modifier = Modifier.padding(top = 10.dp))
                Field(label, { label = it }, "Gmail label", modifier = Modifier.padding(top = 10.dp), onDone = ::connect)
                error?.let { Text(it, fontSize = 14.sp, color = c.error, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp)) }
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.CenterEnd) {
                    PillButton(if (busy) "Connecting…" else "Connect", onClick = ::connect, enabled = ready)
                }
            }
        }
        card("app-password") {
            ListRow(
                "Make an app password",
                summary = "Google Account › Security › 2-Step Verification › App passwords. Briefs keeps it encrypted on this phone.",
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/apppasswords"))) },
            )
        }
        description("Tip: in Gmail, make a filter for your newsletters' senders that applies the label, so new briefs show up here on their own.", key = "tip")
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, onDone: () -> Unit = {},
    /** In a popup: the search bar's grey and hairline, like the other popups' text boxes. */
    popup: Boolean = false,
) {
    val c = OneUi.colors
    Box(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape)
            .then(
                if (popup) Modifier.background(com.tools.app.ui.popupFieldFill(c.isDark)).border(0.6.dp, com.tools.app.ui.pillEdge(c.isDark), CircleShape)
                else Modifier.background(c.tonal),
            )
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, fontSize = 16.sp, color = c.textSecondary)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(fontFamily = SamsungFont, fontSize = 16.sp, color = c.text),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---------------------------------------------------------------- list

@Composable
private fun BriefList(onOpen: (Long) -> Unit, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val items by Briefs.list.collectAsState()
    val syncing by Briefs.syncing.collectAsState()
    val error by Briefs.error.collectAsState()
    var account by remember { mutableStateOf(BriefsAccount.get(context)) }
    var changingLabel by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val unread = items.count { !it.seen }

    // Pull down to refresh, with Samsung's four-dot loader. It shows for a sync you asked for (pulled, or the refresh
    // button); the automatic sync on opening only says "Syncing…" under the title.
    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(syncing) { if (!syncing) pulled = false }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = pulled && syncing,
        onRefresh = { pulled = true; Briefs.sync(context) },
        state = pull,
        indicator = {
            val shown = pulled && syncing
            val f = if (shown) 1f else pull.distanceFraction.coerceIn(0f, 1f)
            if (f > 0f) Box(
                Modifier.align(Alignment.TopCenter)
                    // Just below the back / ⋮ buttons, over the first cards: they sit at the bottom of the title
                    // header while it's open and pin to the top once it folds.
                    .offset {
                        val header = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "__header" }
                        val pinned = (statusTop + com.tools.app.ui.ToolbarHeight).roundToPx()
                        val toolbarBottom = maxOf(header?.let { it.offset + it.size } ?: 0, pinned)
                        androidx.compose.ui.unit.IntOffset(0, toolbarBottom + 10.dp.roundToPx())
                    }
                    .graphicsLayer {
                        // Follows your finger in, growing and fading up, then stays while it syncs.
                        alpha = f
                        scaleX = 0.5f + 0.5f * f; scaleY = scaleX
                        translationY = (f - 1f) * 28.dp.toPx()
                        rotationZ = if (shown) 0f else 270f * pull.distanceFraction
                    },
            ) { com.tools.app.ui.OneUiSpinner(Modifier.size(44.dp)) }
        },
    ) {
        OneUiScaffold(
            title = "Briefs",
            listState = list,
            subtitle = when {
                syncing -> "Syncing…"
                unread > 0 -> "$unread unread"
                items.isNotEmpty() -> "All caught up"
                else -> null
            },
            onBack = onBack,
            actions = {
                // Turns once when pressed, and shows the loader like a pull.
                val spin = remember { androidx.compose.animation.core.Animatable(0f) }
                val spinScope = rememberCoroutineScope()
                Box(Modifier.graphicsLayer { rotationZ = spin.value }) {
                    ToolbarIcon(OneUiIcons.Refresh, "Sync") {
                        pulled = true; Briefs.sync(context)
                        spinScope.launch { spin.snapTo(0f); spin.animateTo(360f, tween(650, easing = com.tools.app.ui.theme.OneUiEasing)); spin.snapTo(0f) }
                    }
                }
                OverflowMenu(listOf("Change label" to { changingLabel = true }, "Sign out" to { signingOut = true }))
            },
        ) {
            error?.let { e ->
                card("error") { ListRow("Couldn't sync", summary = e, summaryColor = c.error, onClick = { Briefs.sync(context) }) }
            }
            if (items.isEmpty()) {
                if (syncing) item("loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { com.tools.app.ui.OneUiSpinner() }
                } else description(
                    "Nothing under \"${account?.label}\" yet. Label an email in Gmail and it shows up here the next time you open Briefs.",
                    key = "empty",
                )
            }
            // Three cards: Today, Yesterday and Past (the past ones show their date).
            val groups = items.groupBy { b ->
                when (dayKey(b.date)) {
                    dayKey(System.currentTimeMillis()) -> "Today"
                    dayKey(System.currentTimeMillis() - 86_400_000L) -> "Yesterday"
                    else -> "Past"
                }
            }
            listOf("Today", "Yesterday", "Past").forEach { group ->
                val briefs = groups[group] ?: return@forEach
                sectionLabel(group, key = "group-$group")
                cardList(briefs, key = { it.uid }) { b ->
                    ListRow(
                        plainTitle(b.subject),
                        summary = listOfNotNull(b.sender, if (group == "Past") shortDate(b.date) else null, "${b.minutes} min read").joinToString(" · "),
                        // Unread briefs get the accent dot at the end, like Samsung Email.
                        trailing = if (b.seen) null else ({ Box(Modifier.size(9.dp).clip(CircleShape).background(c.accent)) }),
                        onClick = { onOpen(b.uid) },
                    )
                }
            }
            if (items.isNotEmpty() && Briefs.lastSync > 0) description("Synced ${timeAgo(Briefs.lastSync)} · ${account?.email}", key = "synced")
        }
    }

    if (changingLabel) {
        var label by remember { mutableStateOf(account?.label.orEmpty()) }
        var busy by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        OneUiDialog(
            "Gmail label", onDismiss = { changingLabel = false },
            buttons = listOf(
                "Cancel" to { changingLabel = false },
                (if (busy) "Checking…" else "Save") to {
                    val a = account?.copy(label = label.trim())
                    if (a != null && a.label.isNotEmpty() && !busy) {
                        busy = true
                        scope.launch {
                            val e = Briefs.test(a)
                            busy = false
                            if (e == null) {
                                BriefsAccount.save(context, a); account = a; changingLabel = false
                                Briefs.clearCache(context); Briefs.sync(context)
                            } else problem = e
                        }
                    }
                },
            ),
        ) {
            Field(label, { label = it }, "Gmail label", popup = true)
            problem?.let { Text(it, fontSize = 14.sp, color = c.error, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
    if (signingOut) OneUiDialog(
        "Sign out?", onDismiss = { signingOut = false },
        buttons = listOf("Cancel" to { signingOut = false }, "Sign out" to { signingOut = false; Briefs.signOut(context); onSignedOut() }),
    ) {
        Text("Briefs forgets the app password and removes the synced emails from this phone. Nothing changes in Gmail.", fontSize = 15.sp, color = c.textSecondary)
    }
}

// ---------------------------------------------------------------- article

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArticleScreen(uid: Long, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    val items by Briefs.list.collectAsState()
    val brief = items.firstOrNull { it.uid == uid }
    if (brief == null) { LaunchedEffect(Unit) { onBack() }; return }
    val next = items.getOrNull(items.indexOf(brief) + 1)
    val latestNext = androidx.compose.runtime.rememberUpdatedState(next)
    val onOpenLatest = androidx.compose.runtime.rememberUpdatedState(onOpen)
    val navDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val prefs = remember { context.getSharedPreferences("briefs_reading", Context.MODE_PRIVATE) }
    var scale by remember { mutableFloatStateOf(prefs.getFloat("scale", 1f)) }
    // "Show in light": the page in One UI's light colours, for newsletters made for a white background. Remembered.
    var lightPage by remember { mutableStateOf(prefs.getBoolean("light", false)) }
    var showImages by remember(uid) { mutableStateOf(brief.address in prefs.getStringSet("imageSenders", emptySet())!!) }
    val article by produceState<Article?>(null, uid, showImages) {
        value = withContext(Dispatchers.Default) {
            BriefHtml.article(Briefs.body(context, uid), showImages, brief.subject)
        }
    }
    LaunchedEffect(uid) { Briefs.markRead(context, uid) }
    var progress by remember(uid) { mutableFloatStateOf(0f) }   // only for the "Next" pill at the end
    var web by remember { mutableStateOf<WebView?>(null) }
    var sizing by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    val speech = rememberSpeech(uid)
    val topDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value + 64f

    Box(Modifier.fillMaxSize().background(c.background)) {
        val a = article
        if (a != null) AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(c.background.toArgb())
                    isVerticalScrollBarEnabled = false
                    // Only for jumping to a section; the email's own scripts and handlers were stripped out.
                    settings.javaScriptEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            when {
                                url.scheme == "gu" && url.host == "next" -> latestNext.value?.let { onOpenLatest.value(it.uid) }
                                url.scheme == "gu" && url.host == "stop" -> speech.stop()
                                url.scheme == "gu" -> showImages = true
                                url.scheme == "http" || url.scheme == "https" -> runCatching {
                                    androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx, url)
                                }
                                else -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript("document.body.classList.toggle('speaking',${speech.speaking})", null)
                            // Back where you left off.
                            val saved = prefs.getFloat("pos_$uid", 0f)
                            if (saved > 0f) view.postDelayed({
                                val total = view.contentHeight * view.resources.displayMetrics.density - view.height
                                if (total > 0) view.scrollTo(0, (total * saved).toInt())
                            }, 150)
                        }
                    }
                    setOnScrollChangeListener { v, _, y, _, _ ->
                        val w = v as WebView
                        val total = w.contentHeight * w.resources.displayMetrics.density - w.height
                        progress = if (total > 0) (y / total).coerceIn(0f, 1f) else 1f
                        prefs.edit().putFloat("pos_$uid", if (progress > 0.98f) 0f else progress).apply()
                    }
                    web = this
                }
            },
            update = { w ->
                w.settings.textZoom = (scale * 100).toInt()
                // Keyed on the article itself: it's rebuilt a moment after the images switch flips, and loading on
                // the flip alone showed the old version and then skipped the new one.
                val key = "${System.identityHashCode(a)}-${c.isDark}-$lightPage-${next?.uid}"
                if (w.tag != key) {
                    w.tag = key
                    val colours = if (lightPage && c.isDark) com.tools.app.ui.theme.OneUiLightColors else c
                    w.setBackgroundColor(colours.background.toArgb())
                    w.loadDataWithBaseURL(
                        null,
                        page(a, brief, colours, topDp, showImages, frost = frost(c), next = next?.let { plainTitle(it.subject) }, pillText = c.text, bottomDp = navDp),
                        "text/html", "utf-8", null,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.align(Alignment.TopCenter)) {
            val bar: @Composable () -> Unit = {
                // No fill of its own: the page draws the frosted strip under it (see page()), as the PDF reader's header.
                ReaderBar(plainTitle(brief.sender), onBack = onBack, alpha = if (a == null) 0.94f else 0f) {
                    OverflowMenu(buildList {
                        if ((a?.headings?.size ?: 0) >= 3) add("Contents" to { contents = true })
                        add("Text size" to { sizing = true })
                        if (c.isDark) add((if (lightPage) "Show in dark" else "Show in light") to {
                            lightPage = !lightPage; prefs.edit().putBoolean("light", lightPage).apply()
                        })
                        add((if (speech.speaking) "Stop reading" else "Read aloud") to {
                            if (speech.speaking) speech.stop() else a?.let { speech.speak(listOf(plainTitle(brief.subject)) + it.paragraphs) }
                        })
                        if ((a?.images ?: 0) > 0) add((if (showImages) "Hide images" else "Show images") to { showImages = !showImages })
                        val senders = prefs.getStringSet("imageSenders", emptySet())!!
                        if (brief.address.isNotEmpty()) {
                            if (brief.address in senders) add("Stop always showing images" to {
                                prefs.edit().putStringSet("imageSenders", senders - brief.address).apply(); showImages = false
                            }) else if ((a?.images ?: 0) > 0) add("Always show images" to {
                                prefs.edit().putStringSet("imageSenders", senders + brief.address).apply(); showImages = true
                            })
                        }
                    })
                }
            }
            // The header keeps the app's own (dark) frosted look over a light page, like the PDF viewer over white pages.
            bar()
        }

        // The bottom pills ("Reading aloud · Stop", "Next: …") are drawn by the page itself, frosted like the header;
        // tell it when reading aloud starts and stops.
        LaunchedEffect(speech.speaking, web) {
            web?.evaluateJavascript("document.body&&document.body.classList.toggle('speaking',${speech.speaking})", null)
        }
    }

    if (sizing) OneUiDialog("Text size", onDismiss = { sizing = false }, buttons = listOf("Done" to { sizing = false })) {
        Text("${(scale * 100).toInt()}%", fontSize = 15.sp, color = c.textSecondary)
        OneUiSlider(
            scale, { scale = (it * 20).toInt() / 20f }, valueRange = 0.8f..1.6f,
            onValueChangeFinished = { prefs.edit().putFloat("scale", scale).apply() },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    if (contents) OneUiDialog("Contents", onDismiss = { contents = false }, buttons = listOf("Close" to { contents = false })) {
        article?.headings?.forEach { (id, text) ->
            Text(
                text, fontSize = 16.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    contents = false
                    web?.evaluateJavascript("document.getElementById('$id').scrollIntoView({behavior:'smooth'})", null)
                }.padding(horizontal = 6.dp, vertical = 11.dp),
            )
        }
    }
}

/** The article page: the cleaned email in One UI's font and colours, headed by its subject and details. */
/** The PDF reader header's see-through grey, as CSS. */
private fun frost(c: OneUiColors): String {
    val f = com.tools.app.reader.frostedBar(c.isDark)
    val argb = f.toArgb()
    return "rgba(${(argb shr 16) and 0xFF},${(argb shr 8) and 0xFF},${argb and 0xFF},${"%.2f".format(java.util.Locale.US, f.alpha)})"
}

private fun page(
    a: Article, b: Brief, c: OneUiColors, topDp: Float, showImages: Boolean, frost: String = frost(c),
    /** The "Next: …" pill: the next brief's title, the header's text colour, and the room the navigation bar takes. */
    next: String? = null, pillText: Color = c.text, bottomDp: Float = 0f,
): String {
    fun hex(color: Color) = String.format("#%06X", color.toArgb() and 0xFFFFFF)
    fun esc(s: String) = org.jsoup.nodes.Entities.escape(s)
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(b.date))
    val imageNote = if (!showImages && a.images > 0) " · <a href=\"gu://images\">Show ${a.images} image${if (a.images == 1) "" else "s"}</a>" else ""
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
:root{color-scheme:${if (c.isDark) "dark" else "light"}}
*{-webkit-tap-highlight-color:transparent}a:focus{outline:none}
html,body{margin:0;background:${hex(c.background)}}
body{padding:${topDp.toInt() + 14}px 24px 150px;color:${hex(c.text)};font-family:sec,SamsungOne,sans-serif;font-size:17px;line-height:1.62;overflow-wrap:anywhere}
a{color:${hex(c.accent)};text-decoration:none}
h1.gu-title{font-size:1.65em;line-height:1.22;margin:0 0 .35em;font-weight:800;letter-spacing:-.01em}
.gu-meta{color:${hex(c.textSecondary)};font-size:.82em;margin-bottom:1.8em}
h1,h2,h3,h4{line-height:1.3;margin:1.4em 0 .5em}
h1{font-size:1.4em}h2{font-size:1.25em}h3{font-size:1.1em}
p{margin:0 0 1em}
div:not(:has(div,p,ul,ol,h1,h2,h3,h4,blockquote)){margin:0 0 .75em}
img{display:block;max-width:100%;height:auto;margin:1em auto;border-radius:14px}
hr{border:0;border-top:1px solid ${hex(c.divider)};margin:1.6em 0}
blockquote{margin:1em 0;padding-left:14px;border-left:3px solid ${hex(c.accent)};color:${hex(c.textSecondary)}}
ul,ol{padding-left:1.3em}li{margin:.3em 0}
pre,code{white-space:pre-wrap;font-size:.9em}
.gu-bar{position:fixed;top:0;left:0;right:0;height:${topDp.toInt()}px;z-index:9;background:$frost;-webkit-backdrop-filter:blur(24px);backdrop-filter:blur(24px)}
.gu-pill{position:fixed;left:50%;bottom:${bottomDp.toInt() + 16}px;z-index:9;max-width:calc(100% - 48px);box-sizing:border-box;padding:13px 20px;border-radius:999px;
 background:$frost;-webkit-backdrop-filter:blur(24px);backdrop-filter:blur(24px);box-shadow:0 6px 18px rgba(0,0,0,.35);
 color:${hex(pillText)};font-size:15px;font-weight:600;line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
 opacity:0;pointer-events:none;transform:translate(-50%,16px);transition:opacity .22s,transform .3s cubic-bezier(.22,.25,0,1)}
.gu-pill.on,body.speaking .gu-tts{opacity:1;pointer-events:auto;transform:translate(-50%,0)}
body.speaking #gu-next{opacity:0;pointer-events:none}
.gu-pill:active{filter:brightness(1.35)}a:not(.gu-pill):active{opacity:.6}
</style></head><body>
<div class="gu-bar"></div>
<h1 class="gu-title">${esc(plainTitle(b.subject))}</h1>
<div class="gu-meta">${esc(plainTitle(b.sender))} · $date · ${b.minutes} min read$imageNote</div>
${a.html}
<a class="gu-pill gu-tts" href="gu://stop">Reading aloud · Stop</a>
${if (next != null) """<a id="gu-next" class="gu-pill" href="gu://next">Next: ${esc(next)}</a>
<script>(function(){var n=document.getElementById('gu-next');function u(){var d=document.documentElement;n.classList.toggle('on',window.innerHeight+window.scrollY>=d.scrollHeight-60);}window.addEventListener('scroll',u,{passive:true});window.addEventListener('resize',u);u();})();</script>""" else ""}
</body></html>"""
}

// ---------------------------------------------------------------- read aloud

private class Speech(private val context: Context) {
    var speaking by mutableStateOf(false)
        private set
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: List<String>? = null

    fun speak(parts: List<String>) {
        speaking = true
        if (ready) say(parts) else {
            pending = parts
            if (tts == null) tts = TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) pending?.let { say(it) } else speaking = false
                pending = null
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { if (id == "last") context.mainExecutor.execute { speaking = false } }
                    @Deprecated("Deprecated in Java") override fun onError(id: String?) { context.mainExecutor.execute { speaking = false } }
                })
            }
        }
    }

    private fun say(parts: List<String>) {
        val t = tts ?: return
        val max = TextToSpeech.getMaxSpeechInputLength() - 1
        val chunks = parts.flatMap { p -> p.chunked(max) }
        chunks.forEachIndexed { i, chunk ->
            t.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == chunks.lastIndex) "last" else "p$i")
        }
    }

    fun stop() { tts?.stop(); speaking = false }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null; ready = false; speaking = false }
}

@Composable
private fun rememberSpeech(key: Any): Speech {
    val context = LocalContext.current
    val speech = remember(key) { Speech(context) }
    // Stops when you leave the brief, and when the app goes to the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speech, lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) speech.stop() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech.shutdown() }
    }
    return speech
}

/** Subjects and names without their emoji ("☕ Hangover pill" → "Hangover pill"). */
private fun plainTitle(s: String): String = BriefHtml.noEmoji(s)

// ---------------------------------------------------------------- dates

private fun dayKey(t: Long): Int { val c = Calendar.getInstance().apply { timeInMillis = t }; return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR) }

private fun dayLabel(t: Long): String = when (dayKey(t)) {
    dayKey(System.currentTimeMillis()) -> "Today"
    dayKey(System.currentTimeMillis() - 86_400_000L) -> "Yesterday"
    else -> SimpleDateFormat(
        if (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "EEEE, MMM d" else "MMM d, yyyy",
        Locale.getDefault(),
    ).format(Date(t))
}

private fun shortDate(t: Long): String = SimpleDateFormat(
    if (Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) "MMM d" else "MMM d, yyyy",
    Locale.getDefault(),
).format(Date(t))

private fun timeAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t))
    }
}
