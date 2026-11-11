package com.tools.app.apk

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

    const val FILE_TITLE = "Split APK file"

    private fun launch(context: Context, title: String, work: suspend (Context, (String) -> Unit) -> State) {
        if (state.value is State.Running) return
        val ctx = context.applicationContext
        scope.launch {
            state.value = State.Running(title, "Starting…")
            state.value = try {
                work(ctx) { s -> state.value = State.Running(title, s) }
            } catch (e: Throwable) {
                ApkTools.clearCache(ctx)
                State.Failed(title, if (e is OutOfMemoryError) "This app is too large to merge on the phone" else e.message ?: e.javaClass.simpleName)
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
    val bundlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { selected = null; ApkJob.runFile(context, uri) }
    }

    val shown = apps.orEmpty()
    OneUiScaffold(title = "APK extractor", onBack = onBack) {
        description("Save apps as APK files in ${ApkTools.FOLDER}. Split apps and bundle files are merged into one APK you can install anywhere.")
        cardList(listOf(Unit), key = { "bundle-file" }, dividerStart = IconRowDivider) {
            ListRow(
                "Merge a split APK file",
                summary = ".apks, .xapk or .apkm bundle → one installable APK",
                leading = { ColorIcon(Icons.AutoMirrored.Rounded.CallMerge, IconColors.Blue) },
                onClick = { if (job !is ApkJob.State.Running) { ApkJob.state.value = ApkJob.State.Idle; bundlePicker.launch(arrayOf("*/*")) } },
            )
        }
        sectionLabel("Installed apps")
        if (apps == null) description("Loading apps…", key = "loading")
        cardList(shown, key = { it.packageName }, dividerStart = IconRowDivider + 12.dp) { app ->
            ListRow(
                app.label,
                summary = listOfNotNull(
                    app.versionName.takeIf { it.isNotBlank() },
                    size(app.sizeBytes),
                    if (app.isSplit) "Split · ${app.splits.size + 1} parts" else null,
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
                Text("Saved to ${ApkTools.FOLDER}\n${st.fileName}", fontSize = 15.sp, lineHeight = 21.sp, color = c.text)
                if (st.merged) Text(
                    "The merged APK is signed by Good Utilities, not by the original developer. Uninstall any installed version before installing it; it won't receive Play updates.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp),
                )
            }
            forThis && st is ApkJob.State.Failed -> OneUiDialog(title, onDismiss = close, buttons = listOf("OK" to close)) {
                Text("Couldn't save this.\n${st.message}", fontSize = 15.sp, lineHeight = 21.sp, color = c.text)
            }
            app != null -> {
                val buttons = if (app.isSplit) listOf(
                    "Cancel" to close,
                    "Save .apks" to { ApkJob.run(context, app, 2) },
                    "Merge APK" to { ApkJob.run(context, app, 1) },
                ) else listOf("Cancel" to close, "Save APK" to { ApkJob.run(context, app, 0) })
                OneUiDialog(app.label, onDismiss = close, buttons = buttons) {
                    Text(
                        if (app.isSplit)
                            "Installed as ${app.splits.size + 1} parts (${size(app.sizeBytes)}).\n\n" +
                                "Merge APK: one installable APK, re-signed by Good Utilities.\n" +
                                "Save .apks: all parts zipped with the original signature, for split installers like SAI."
                        else "Single APK, ${size(app.sizeBytes)}. It is copied with its original signature.",
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
