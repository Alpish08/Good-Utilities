package com.tools.app.apk

import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.material.icons.automirrored.rounded.CallMerge

import androidx.compose.material.icons.Icons

import com.tools.app.ui.OneUiIcons
import android.content.Context
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.IconRowDivider
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.cardList
import com.tools.app.ui.collapseHeader
import com.tools.app.ui.description
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Extraction state, kept outside the screen so a long merge survives leaving the page. */
private object ApkJob {
    sealed interface State {
        data object Idle : State
        data class Running(val title: String, val step: String) : State
        data class Done(val title: String, val fileName: String, val merged: Boolean) : State
        data class Failed(val title: String, val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun run(context: Context, app: InstalledApp, how: Int) = launch(context, app.label) { ctx, step ->
        when (how) {
            0 -> State.Done(app.label, ApkTools.extract(ctx, app), merged = false)
            1 -> State.Done(app.label, ApkTools.merge(ctx, app, step), merged = true)
            else -> State.Done(app.label, ApkTools.exportApks(ctx, app, step), merged = false)
        }
    }

    fun runFile(context: Context, uri: android.net.Uri) = launch(context, FILE_TITLE) { ctx, step ->
        State.Done(FILE_TITLE, ApkTools.mergeFile(ctx, uri, step), merged = true)
    }

    val FILE_TITLE get() = t(R.string.apk_split_apk_file)

    private fun launch(context: Context, title: String, work: suspend (Context, (String) -> Unit) -> State) {
        if (state.value is State.Running) return
        val ctx = context.applicationContext
        scope.launch {
            state.value = State.Running(title, t(R.string.video_starting))
            state.value = try {
                work(ctx) { s -> state.value = State.Running(title, s) }
            } catch (e: Throwable) {
                ApkTools.clearCache(ctx)
                State.Failed(title, if (e is OutOfMemoryError) t(R.string.apk_this_app_is_too_large_to_merge_on_the) else e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

@Composable
fun ApkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    val job by ApkJob.state.collectAsState()
    LaunchedEffect(Unit) { apps = ApkTools.listApps(context) }
    val bundlePicker = rememberLauncherForActivityResult(com.tools.app.ui.FilesPick()) { uri ->
        if (uri != null) { selected = null; ApkJob.runFile(context, uri) }
    }

    // Search opens from the toolbar as Home's floating bottom pill; while it's open only the apps are listed.
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var searching by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val closeSearch: () -> Unit = {
        query = ""; searching = false; focusManager.clearFocus()
        scope.launch { kotlinx.coroutines.delay(60); listState.collapseHeader() }
    }
    androidx.activity.compose.BackHandler(enabled = searching) { closeSearch() }

    val q = query.trim()
    val shown = apps.orEmpty().filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    OneUiScaffold(
        title = t(R.string.home_apk_extractor), onBack = onBack, listState = listState,
        actions = { com.tools.app.ui.TipsMenu(listOf(
            com.tools.app.ui.Tip(t(R.string.apk_where_they_go), t(R.string.apk_apps_are_saved_as_apk_files_in, ApkTools.FOLDER)),
            com.tools.app.ui.Tip(t(R.string.apk_split_apps), t(R.string.apk_split_apps_and_bundle_files_apks_xapk)),
        )) },
        bottomBar = { com.tools.app.ui.PageSearch(searching, query, { query = it }, onOpen = { searching = true }, onClose = closeSearch) },
    ) {
        if (!searching) {
        cardList(listOf(Unit), key = { "bundle-file" }) {
            ListRow(
                t(R.string.apk_merge_a_split_apk_file),
                summary = t(R.string.apk_bundle_to_one_apk),
                onClick = { if (job !is ApkJob.State.Running) { ApkJob.state.value = ApkJob.State.Idle; bundlePicker.launch(arrayOf("*/*")) } },
            )
        }
        }
        sectionLabel(if (searching && q.isNotEmpty() && shown.isEmpty()) t(R.string.apk_no_apps_found) else t(R.string.apk_installed_apps), key = "apps-label")
        if (apps == null) description(t(R.string.apk_loading_apps), key = "loading")
        cardList(shown, key = { it.packageName }, dividerStart = IconRowDivider + 12.dp) { app ->
            ListRow(
                app.label,
                summary = listOfNotNull(
                    app.versionName.takeIf { it.isNotBlank() },
                    size(app.sizeBytes),
                    if (app.isSplit) t(R.string.apk_split_parts, app.splits.size + 1) else null,
                ).joinToString(" · "),
                leading = { AppIcon(app.packageName) },
                onClick = { if (job !is ApkJob.State.Running) { ApkJob.state.value = ApkJob.State.Idle; selected = app } },
            )
        }
    }

    // Action / progress dialog.
    val st = job
    val jobTitle = when (st) {
        is ApkJob.State.Running -> st.title
        is ApkJob.State.Done -> st.title
        is ApkJob.State.Failed -> st.title
        else -> null
    }
    val app = selected
    val title = app?.label ?: jobTitle
    if (title != null) {
        val forThis = jobTitle == title
        val close = { selected = null; if (job !is ApkJob.State.Running) ApkJob.state.value = ApkJob.State.Idle }
        when {
            forThis && st is ApkJob.State.Running -> OneUiDialog(title, onDismiss = {}, buttons = emptyList(), dismissible = false) {
                Text(st.step, fontSize = 15.sp, color = c.textSecondary)
                Indeterminate()
            }
            forThis && st is ApkJob.State.Done -> OneUiDialog(title, onDismiss = close, buttons = listOf("OK" to close)) {
                Text(t(R.string.apk_saved_to_n, ApkTools.FOLDER, st.fileName), fontSize = 15.sp, lineHeight = 21.sp, color = c.text)
                if (st.merged) Text(
                    t(R.string.apk_the_merged_apk_is_signed_by_good_utili),
                    fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp),
                )
            }
            forThis && st is ApkJob.State.Failed -> OneUiDialog(title, onDismiss = close, buttons = listOf("OK" to close)) {
                Text(t(R.string.apk_couldn_t_save_this_n, st.message), fontSize = 15.sp, lineHeight = 21.sp, color = c.text)
            }
            app != null -> {
                val buttons = if (app.isSplit) listOf(
                    t(R.string.ui_cancel) to close,
                    t(R.string.apk_save_apks) to { ApkJob.run(context, app, 2) },
                    t(R.string.apk_merge_apk) to { ApkJob.run(context, app, 1) },
                ) else listOf(t(R.string.ui_cancel) to close, t(R.string.apk_save_apk) to { ApkJob.run(context, app, 0) })
                OneUiDialog(app.label, onDismiss = close, buttons = buttons) {
                    Text(
                        if (app.isSplit)
                            t(R.string.apk_installed_as_parts_n_n, app.splits.size + 1, size(app.sizeBytes)) +
                                t(R.string.apk_merge_apk_one_installable_apk_re_signe) +
                                t(R.string.apk_save_apks_all_parts_zipped_with_the_or)
                        else t(R.string.apk_single_apk_it_is_copied_with_its_origi, size(app.sizeBytes)),
                        fontSize = 15.sp, lineHeight = 21.sp, color = c.text,
                    )
                }
            }
        }
    }
}

/** Sweeping bar for steps without a percentage. */
@Composable
private fun Indeterminate() {
    val c = OneUi.colors
    val t = rememberInfiniteTransition(label = "busy")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100)), label = "sweep")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp).height(4.dp).clip(CircleShape).background(c.divider)) {
        Box(Modifier.offset(x = maxWidth * 0.75f * x).width(maxWidth * 0.25f).fillMaxHeight().clip(CircleShape).background(c.accent))
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(120, 120).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp)) { icon?.let { Image(it, null, Modifier.size(40.dp)) } }
}

private fun size(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    else -> "${bytes / 1024} KB"
}
