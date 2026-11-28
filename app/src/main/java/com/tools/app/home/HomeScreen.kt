package com.tools.app.home

import com.tools.app.R
import com.tools.app.ui.t
import com.tools.app.ui.tp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Newspaper

import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import com.tools.app.ui.theme.OneUi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic

import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.MenuBook

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.tools.app.ui.collapseHeader
import kotlinx.coroutines.launch
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.tools.app.ui.card
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.tools.app.Route
import com.tools.app.applock.AppLock
import com.tools.app.battery.ChargeLimit
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.Phase
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.IconRowDivider
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.cardList
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors

private data class Tool(val route: Route, val title: String, val summary: String, val icon: ImageVector, val color: Color)

/** Home's scroll position, kept by MainActivity so it survives opening a tool. */
class HomeState {
    val list = LazyListState()
    /** Selected bottom tab (Media, Files, Images, Device, Miscellaneous); -1 = none, every tool shows. */
    var tab by androidx.compose.runtime.mutableIntStateOf(-1)
}

@Composable
fun HomeScreen(state: HomeState, open: (Route) -> Unit) {
    val context = LocalContext.current
    val lock by AppLock.config.collectAsState()
    val music by MusicDownloader.state.collectAsState()
    val limit = ChargeLimit.activeLimit(context)
    remember { com.tools.app.card.CardStore.load(context) }
    val cards by com.tools.app.card.CardStore.state.collectAsState()
    // Battery health for the Miscellaneous row (Samsung's figures when Good Utilities may read them).
    var battery by remember { mutableStateOf<com.tools.app.battery.BatterySnapshot?>(null) }
    LaunchedEffect(Unit) {
        battery = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.tools.app.battery.BatteryHealth.read(context).copy(samsung = com.tools.app.battery.BatteryHealth.samsung(context))
        }
    }
    // Re-checked every time you come back (e.g. from the All files access page in Settings).
    var access by remember { mutableStateOf(com.tools.app.Storage.hasAccess()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) access = com.tools.app.Storage.hasAccess()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val musicSummary = when (music.phase) {
        Phase.Downloading -> t(R.string.home_downloading_of, minOf(music.finishedCount + 1, music.totalQueued), music.totalQueued)
        else -> t(R.string.home_youtube_songs_and_playlists_to_mp3)
    }
    val groups = listOf(
        t(R.string.home_media) to listOf(
            Tool(Route.Music, t(R.string.home_music_downloader), musicSummary, Icons.Rounded.MusicNote, IconColors.Red),
            Tool(Route.VideoDownloader, t(R.string.home_video_downloader), t(R.string.home_youtube_tiktok_instagram_x_and_more), Icons.Rounded.VideoLibrary, IconColors.Blue),
            Tool(Route.AudioMerge, t(R.string.home_audio_merger), t(R.string.home_join_songs_into_one_mp3_with_a_crossfa), Icons.AutoMirrored.Rounded.QueueMusic, IconColors.Orange),
            Tool(Route.AudioTrim, t(R.string.home_audio_trimmer), t(R.string.home_cut_a_song_without_losing_quality), Icons.Rounded.ContentCut, IconColors.Purple),
        ),
        t(R.string.home_files) to listOf(
            Tool(Route.Reader, t(R.string.home_document_reader), t(R.string.home_pdfs_word_files_and_text_one_ui_style), Icons.AutoMirrored.Rounded.MenuBook, IconColors.Orange),
            Tool(Route.PdfEditor, t(R.string.home_pdf_editor), t(R.string.home_merge_split_reorder_sign_and_annotate), Icons.Rounded.PictureAsPdf, IconColors.Red),
            Tool(Route.Converter, t(R.string.home_file_converter), t(R.string.home_images_documents_audio_and_video), Icons.Rounded.SwapHoriz, IconColors.Teal),
            Tool(Route.Print, t(R.string.home_print), t(R.string.home_print_pdfs_photos_and_documents), Icons.Rounded.Print, IconColors.Blue),
            Tool(Route.Compressor, t(R.string.home_compressor), t(R.string.home_shrink_videos_audio_and_photos), Icons.Rounded.Compress, IconColors.Purple),
            Tool(Route.ApkExtractor, t(R.string.home_apk_extractor), t(R.string.home_save_apps_as_apk_merging_split_bundles), Icons.Rounded.Android, IconColors.Green),
        ),
        t(R.string.home_images) to listOf(
            Tool(Route.ColorPicker, t(R.string.home_color_picker), t(R.string.home_sample_any_pixel_get_hex_rgb_and_palet), Icons.Rounded.Colorize, IconColors.Pink),
            Tool(Route.Blur, t(R.string.home_image_blur), t(R.string.home_blur_a_whole_photo_or_brush_over_detai), Icons.Rounded.BlurOn, IconColors.Purple),
            Tool(Route.BackgroundRemover, t(R.string.home_background_remover), t(R.string.home_cut_out_people_pets_and_objects_as_png), Icons.Rounded.AutoFixHigh, IconColors.Teal),
            Tool(Route.IconMaker, t(R.string.home_icon_maker), t(R.string.home_put_any_logo_on_your_icon_pack_s_empty), Icons.Rounded.Category, IconColors.Orange),
        ),
        t(R.string.home_device) to listOf(
            Tool(
                Route.AppLock, t(R.string.home_app_lock),
                if (lock.enabled && lock.locked.isNotEmpty()) tp(R.plurals.home_apps_locked, lock.locked.size) else t(R.string.home_lock_apps_with_fingerprint_face_or_pin),
                Icons.Rounded.Lock, IconColors.Blue,
            ),
            Tool(
                Route.PrivateDns, t(R.string.home_private_dns),
                if (com.tools.app.dns.PrivateDns.isCustom(context)) t(R.string.home_custom, com.tools.app.dns.PrivateDns.host(context)) else t(R.string.home_automatic),
                Icons.Rounded.Language, IconColors.Teal,
            ),
            Tool(
                Route.ChargeLimit, t(R.string.home_charge_limit),
                limit?.let { t(R.string.home_stops_charging_at, it) } ?: t(R.string.home_custom_battery_protection_and_bypass_c),
                Icons.Rounded.BatteryChargingFull, IconColors.Green,
            ),
            Tool(
                Route.Battery, t(R.string.home_battery_info),
                battery?.let { b -> b.healthPercent?.let { t(R.string.home_health, (if (b.healthIsSamsung) "" else "≈ ") + "$it%") + (b.totalCycles?.let { c -> t(R.string.home_cycles, c) } ?: "") } } ?: t(R.string.home_health_temperature_and_charging_speed),
                com.tools.app.ui.OneUiIcons.BatteryInfo, IconColors.Gray,
            ),
        ),
        t(R.string.home_miscellaneous) to listOf(
            Tool(
                Route.CardBalance, t(R.string.home_card_balance),
                cards.primary?.let { "${com.tools.app.card.CardStore.format(it.balanceCents)} · ${it.name}" } ?: t(R.string.home_track_expenses_and_payments),
                Icons.Rounded.CreditCard, IconColors.Blue,
            ),
            Tool(Route.IdCards, t(R.string.home_virtual_cards), t(R.string.home_your_ids_front_and_back_locked_and_enc), Icons.Rounded.Badge, IconColors.Orange),
            Tool(Route.Briefs, t(R.string.home_briefs), t(R.string.home_newsletters_and_articles_from_gmail_ma), Icons.Rounded.Newspaper, IconColors.Red),
            Tool(Route.Japanese, t(R.string.home_japanese_reader), t(R.string.home_readings_and_meanings_for_japanese_on), Icons.Rounded.Translate, IconColors.Pink),
            Tool(Route.QrCode, t(R.string.home_qr_code), t(R.string.home_turn_a_link_or_any_text_into_a_qr_code), Icons.Rounded.QrCode2, IconColors.Teal),
            Tool(Route.Random, t(R.string.home_random), t(R.string.home_numbers_in_any_range_or_roll_some_dice), Icons.Rounded.Casino, IconColors.Purple),
        ),
    )

    // Search opens from the toolbar's search button as a full-width bar at the bottom.
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val morph = remember { com.tools.app.ui.SearchMorphProgress() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun closeSearch() {
        query = ""; searching = false; focusManager.clearFocus()
        // Back to the tab's list with the title folded, not scrolled past it.
        scope.launch { kotlinx.coroutines.delay(60); state.list.collapseHeader() }
    }
    androidx.activity.compose.BackHandler(enabled = searching) { closeSearch() }
    val q = query.trim()
    val matches = if (q.isEmpty()) emptyList() else groups.flatMap { it.second }.filter {
        it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true)
    }
    // Opening search folds the big title away so the results have room above the keyboard.
    // Re-checked as results change: a short results list first jumps back to the top, then gets room to fold again.
    LaunchedEffect(searching, q) {
        if (!searching) return@LaunchedEffect
        kotlinx.coroutines.delay(60)
        if (state.list.firstVisibleItemIndex == 0) state.list.collapseHeader()
    }

    // Clock-style icon tabs: one category at a time (One UI line icons; filled while selected).
    val tabs = listOf(
        com.tools.app.ui.PillTab(t(R.string.home_media), com.tools.app.ui.OneUiIcons.TabMedia, com.tools.app.ui.OneUiIcons.TabMediaOn),
        com.tools.app.ui.PillTab(t(R.string.home_files), com.tools.app.ui.OneUiIcons.TabFiles, com.tools.app.ui.OneUiIcons.TabFilesOn),
        com.tools.app.ui.PillTab(t(R.string.home_images), com.tools.app.ui.OneUiIcons.TabImages, com.tools.app.ui.OneUiIcons.TabImagesOn),
        com.tools.app.ui.PillTab(t(R.string.home_device), com.tools.app.ui.OneUiIcons.TabDevice, com.tools.app.ui.OneUiIcons.TabDeviceOn),
        com.tools.app.ui.PillTab(t(R.string.home_misc), com.tools.app.ui.OneUiIcons.TabMisc, com.tools.app.ui.OneUiIcons.TabMiscOn),
    )
    var choosingGlow by remember { mutableStateOf(false) }
    if (choosingGlow) GlowPicker(onDismiss = { choosingGlow = false })
    val haze = remember { dev.chrisbanes.haze.HazeState() }

    androidx.compose.runtime.CompositionLocalProvider(com.tools.app.ui.LocalHazeState provides haze) {
    OneUiScaffold(
        title = "Good Utilities",
        subtitle = groups.sumOf { it.second.size }.let { tp(R.plurals.home_tools, it) },
        listState = state.list,
        // With no tab chosen, the title scrolls away and the buttons float on shadowed chips; inside a tab, and
        // while searching, the title stays pinned.
        floatingToolbar = state.tab < 0 && !searching,
        // The list fades out toward the tab bar everywhere.
        fadeUnderBottomBar = true,
        actions = {
            com.tools.app.ui.ToolbarIcon(com.tools.app.ui.OneUiIcons.Search, t(R.string.home_search)) { searching = true }
            OverflowMenu(listOf(t(R.string.home_background) to { choosingGlow = true }, t(R.string.home_about) to { open(Route.About) }))
        },
        bottomBar = {
            // The two bars cross-fade in place. Faded per draw (not through an offscreen layer), so the bars,
            // which sit a few dp outside their own bounds, are never cropped mid-fade.
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                // The tabs are toggles: tap one to show only its tools, tap it again to see everything.
                // Search morphs the tab pill itself into the search bar (330 × 56 dp → full width above the keyboard):
                // while it changes shape it draws the pill, and the tabs fade out as the search field fades in.
                com.tools.app.ui.SearchMorph(
                    searching, query, { query = it }, onOpen = { searching = true }, onClose = ::closeSearch,
                    restWidth = 330.dp, restHeight = 58.dp, restOffset = 4.dp, hiddenAtRest = true, fadeContent = true, progress = morph,
                )
                // Drawn over the morphing bar so the tabs can fade out on top of it.
                com.tools.app.ui.TabPill(
                    tabs, state.tab, { state.tab = if (state.tab == it) -1 else it },
                    chrome = !searching && morph.value == 0f,
                    // Gone by halfway through the morph, as the search field fades in; riding up with the bar.
                    contentAlpha = (1f - morph.value / 0.5f).coerceIn(0f, 1f),
                    contentLift = morph.lift,
                )
            }
        },
    ) {
        if (searching && q.isNotEmpty()) {
            sectionLabel(if (matches.isEmpty()) t(R.string.home_no_tools_found) else t(R.string.home_results), key = "search-label")
            if (matches.isNotEmpty()) cardList(matches, key = { "found-" + it.route.name }, dividerStart = IconRowDivider) { t ->
                ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { closeSearch(); open(t.route) })
            }
            return@OneUiScaffold
        }
        if (!access) {
            card(key = "storage-access") {
                ListRow(
                    t(R.string.home_allow_all_files_access),
                    summary = t(R.string.home_needed_to_save_everything_to_internal, com.tools.app.Storage.ROOT),
                    leading = { ColorIcon(Icons.Rounded.Folder, IconColors.Orange) },
                    trailing = {
                        com.tools.app.ui.PillButton(t(R.string.home_allow), onClick = { context.startActivity(com.tools.app.Storage.accessIntent(context)) }, height = 38.dp)
                    },
                    onClick = { context.startActivity(com.tools.app.Storage.accessIntent(context)) },
                )
            }
        }
        // No tab chosen: every group, one card each.
        if (state.tab < 0) {
            groups.forEach { (label, tools) ->
                sectionLabel(label, key = "all-$label")
                cardList(tools, key = { "all-" + it.route.name }, dividerStart = IconRowDivider) { t ->
                    ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { open(t.route) })
                }
            }
            return@OneUiScaffold
        }
        // The selected tab's tools (switching tabs swaps them instantly, like Samsung's apps).
        item(key = "tab-content") {
            run {
                val tab = state.tab
                val (label, tools) = groups[tab]
                androidx.compose.foundation.layout.Column {
                    Text(
                        label, fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = OneUi.colors.textSecondary,
                        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 14.dp),
                    )
                    com.tools.app.ui.RoundCard {
                        tools.forEachIndexed { i, t ->
                            ListRow(t.title, summary = t.summary, leading = { ColorIcon(t.icon, t.color) }, onClick = { open(t.route) })
                            if (i != tools.lastIndex) com.tools.app.ui.CardDivider(IconRowDivider)
                        }
                    }
                }
            }
        }
    }
    }
}

/** Home → Background: the glow choices, applied live behind this dialog. */
@Composable
private fun GlowPicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    com.tools.app.ui.OneUiDialog(t(R.string.home_background), onDismiss, buttons = listOf(t(R.string.home_done) to onDismiss)) {
        com.tools.app.ui.theme.GlowStyle.entries.forEach { g ->
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .clickable { com.tools.app.ui.theme.Glow.choose(context, g) }
                    .padding(horizontal = 6.dp, vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                com.tools.app.ui.RadioDot(com.tools.app.ui.theme.Glow.style == g)
                Text(t(g.label), fontSize = 16.sp, color = OneUi.colors.text, modifier = Modifier.padding(start = 14.dp).weight(1f))
            }
        }
    }
}

/** Shows [content] while [visible], fading in and out without clipping it to its bounds. */
@Composable
private fun FadeSlot(visible: Boolean, content: @Composable () -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f,
        if (visible) androidx.compose.animation.core.tween(220, delayMillis = 60) else androidx.compose.animation.core.tween(150),
        label = "fadeSlot",
    )
    if (!visible && alpha == 0f) return
    androidx.compose.foundation.layout.Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        },
    ) { content() }
}
