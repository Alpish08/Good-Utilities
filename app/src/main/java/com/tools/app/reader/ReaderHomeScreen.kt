package com.tools.app.reader

import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.Storage
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Extensions = setOf("pdf", "docx", "txt", "md", "markdown")

/** file:// URIs can't be passed in intents; serve app files (Good Utilities folder, cache) through our FileProvider. */
fun contentUri(ctx: Context, uri: Uri): Uri =
    if (uri.scheme == "file") runCatching {
        androidx.core.content.FileProvider.getUriForFile(ctx, "com.tools.app.files", java.io.File(uri.path ?: ""))
    }.getOrDefault(uri) else uri

fun openInReader(ctx: Context, uri: Uri, kind: Kind) {
    ctx.startActivity(
        Intent(ctx, ReaderActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(contentUri(ctx, uri), kind.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

/** Library page: open a file, recents, files saved by Good Utilities, and how to make One Reader the default. */
@Composable
fun ReaderHomeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var recents by remember { mutableStateOf(ReaderStore.recents(ctx)) }
    var docs by remember { mutableStateOf<List<File>>(emptyList()) }
    var broken by remember { mutableStateOf<Recent?>(null) }
    // Multi-select (long-press a recent), and what was just removed, for Undo.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var removed by remember { mutableStateOf<List<Recent>>(emptyList()) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    fun removeRecents(gone: List<Recent>) {
        removed.forEach { ReaderStore.release(ctx, it.uri) } // an earlier Undo chance has passed
        gone.forEach { ReaderStore.remove(ctx, it.uri, release = false) }
        recents = ReaderStore.recents(ctx); removed = gone
    }
    LaunchedEffect(removed) {
        if (removed.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(6000)
        removed.forEach { ReaderStore.release(ctx, it.uri) }; removed = emptyList()
    }
    // Leaving the page ends the Undo chance.
    val removedNow by androidx.compose.runtime.rememberUpdatedState(removed)
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { removedNow.forEach { ReaderStore.release(ctx, it.uri) } } }

    LaunchedEffect(Unit) {
        docs = withContext(Dispatchers.IO) {
            if (!Storage.hasAccess()) emptyList()
            else Storage.dir(Storage.Folder.Documents).listFiles { f -> f.isFile && !f.name.startsWith(".") && f.extension.lowercase() in Extensions }
                ?.sortedByDescending { it.lastModified() }?.take(100) ?: emptyList()
        }
    }

    val picker = rememberLauncherForActivityResult(com.tools.app.ui.FilesPick()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            scope.launch {
                val info = withContext(Dispatchers.IO) { DocInfo.resolve(ctx, uri) }
                openInReader(ctx, uri, info.kind)
            }
        }
    }

    fun openRecent(r: Recent) {
        scope.launch {
            val uri = Uri.parse(r.uri)
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
            }
            if (ok) openInReader(ctx, uri, r.kind) else broken = r
        }
    }

    val allUris = recents.map { it.uri }
    OneUiScaffold(
        title = t(R.string.reader_documents), onBack = onBack,
        selection = if (selecting) com.tools.app.ui.Selection(
            count = selected.size,
            allSelected = allUris.isNotEmpty() && selected.size == allUris.size,
            onToggleAll = { selected = if (selected.size == allUris.size) emptySet() else allUris.toSet() },
            onCancel = { selecting = false; selected = emptySet() },
        ) else null,
        bottomBar = if (selecting) ({
            com.tools.app.ui.SelectionActions(
                listOf(
                    Triple(OneUiIcons.Delete, t(R.string.audio_remove)) {
                        removeRecents(recents.filter { it.uri in selected }); selecting = false; selected = emptySet()
                    },
                ),
                enabled = selected.isNotEmpty(),
            )
        }) else null,
        actions = {
            com.tools.app.ui.TipsMenu(listOf(
                com.tools.app.ui.Tip(t(R.string.reader_make_it_your_default), t(R.string.reader_open_any_pdf_in_my_files_choose_one_re)),
                com.tools.app.ui.Tip(t(R.string.reader_clear_recents), t(R.string.reader_tap_the_bin_beside_a_document_to_take)),
            ))
        },
    ) {
        card("open") {
            ListRow(
                t(R.string.reader_open_file), summary = t(R.string.reader_pdf_word_and_text_files),
                onClick = { picker.launch(SupportedMimes) },
            )
        }

        if (removed.isNotEmpty()) {
            val gone = removed
            card("undo") {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (gone.size == 1) t(R.string.reader_removed_from_recents) else t(R.string.reader_removed_from_recents_2, gone.size),
                        fontSize = 15.sp, color = OneUi.colors.text, modifier = Modifier.weight(1f),
                    )
                    Text(
                        t(R.string.reader_undo), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable { ReaderStore.restore(ctx, gone); recents = ReaderStore.recents(ctx); removed = emptyList() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (recents.isNotEmpty()) {
            sectionLabel(t(R.string.reader_recent))
            cardList(recents, key = { "r-" + it.uri }) { r ->
                ListRow(
                    r.name, summary = recentSummary(r),
                    leading = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.tools.app.ui.SelectionCheck(selecting, r.uri in selected)
                            Thumb(r.uri, r.size, r.kind)
                        }
                    },
                    trailing = if (selecting) null else ({ RemoveButton { removeRecents(listOf(r)) } }),
                    onClick = {
                        if (selecting) selected = if (r.uri in selected) selected - r.uri else selected + r.uri
                        else openRecent(r)
                    },
                    onLongClick = { if (!selecting) { selecting = true; selected = setOf(r.uri) } },
                )
            }
        }

        if (docs.isNotEmpty()) {
            sectionLabel(t(R.string.reader_good_utilities_documents))
            cardList(docs, key = { "d-" + it.path }) { f ->
                val kind = kindOfName(f.name)
                ListRow(
                    f.name, summary = Formatter.formatShortFileSize(ctx, f.length()) + " · " + relative(f.lastModified()),
                    leading = { Thumb(Uri.fromFile(f).toString(), f.length(), kind) },
                    onClick = { openInReader(ctx, Uri.fromFile(f), kind) },
                )
            }
        }
        if (recents.isEmpty() && docs.isEmpty()) description(t(R.string.reader_documents_you_open_show_up_here_with_t))
    }

    broken?.let { r ->
        OneUiDialog(
            t(R.string.reader_can_t_open_file), { broken = null },
            listOf(t(R.string.ui_cancel) to { broken = null }, t(R.string.audio_remove) to { ReaderStore.remove(ctx, r.uri); recents = ReaderStore.recents(ctx); broken = null }),
        ) {
            Text(t(R.string.reader_was_moved_or_deleted_or_one_reader_no, r.name), fontSize = 14.5.sp, color = OneUi.colors.textSecondary)
        }
    }
}

/** The bin beside a recent: takes it off the list (with Undo). */
@Composable
private fun RemoveButton(onClick: () -> Unit) {
    var label by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    Box(
        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).combinedClickable(onClick = onClick, onLongClick = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); label = true
        }),
        contentAlignment = Alignment.Center,
    ) {
        if (label) com.tools.app.ui.IconLabel(t(R.string.reader_remove_from_recents)) { label = false }
        Icon(OneUiIcons.Delete, t(R.string.reader_remove_from_recents), tint = OneUi.colors.text, modifier = Modifier.size(22.dp))
    }
}

private fun kindOfName(n: String) = when (n.substringAfterLast('.', "").lowercase()) {
    "pdf" -> Kind.Pdf; "docx" -> Kind.Docx; "md", "markdown" -> Kind.Markdown; else -> Kind.Text
}

private fun recentSummary(r: Recent): String {
    val whenText = relative(r.opened)
    return if (r.kind == Kind.Pdf || r.kind == Kind.Docx) {
        if (r.pages > 0) t(R.string.reader_page_of, r.page + 1, r.pages, whenText) else whenText
    } else whenText
}

private fun relative(t: Long): String {
    val now = System.currentTimeMillis()
    if (now - t < 60_000) return t(R.string.reader_just_now)
    val a = Calendar.getInstance().apply { timeInMillis = t }
    val b = Calendar.getInstance()
    val days = (startOfDay(b) - startOfDay(a)) / 86_400_000L
    return when {
        days <= 0 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
        days == 1L -> "yesterday"
        days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(t))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(t))
    }
}

private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

// ---- thumbnails ----

private object Thumbs {
    private val mem = object : LruCache<String, Bitmap>(8 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }

    suspend fun get(ctx: Context, uri: String, size: Long): Bitmap? = withContext(Dispatchers.IO) {
        val key = (uri + "|" + size).hashCode().toUInt().toString(16)
        mem.get(key)?.let { return@withContext it }
        val dir = File(ctx.cacheDir, "reader_thumbs").apply { mkdirs() }
        val f = File(dir, "$key.jpg")
        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.path)?.let { mem.put(key, it); return@withContext it }
        val bmp = runCatching {
            val u = Uri.parse(uri)
            val fd: ParcelFileDescriptor = ctx.contentResolver.openFileDescriptor(u, "r") ?: return@runCatching null
            fd.use {
                PdfRenderer(it).use { r ->
                    r.openPage(0).use { p ->
                        val w = 160
                        val h = (w * p.height / p.width.toFloat()).toInt().coerceIn(1, 400)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                            b.eraseColor(AColor.WHITE)
                            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }.getOrNull() ?: return@withContext null
        runCatching { f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
        mem.put(key, bmp)
        bmp
    }
}

@Composable
private fun Thumb(uri: String, size: Long, kind: Kind) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri, size) { if (kind == Kind.Pdf) value = Thumbs.get(ctx, uri, size) }
    val shape = RoundedCornerShape(8.dp)
    val b = bmp
    if (b != null) {
        Image(
            b.asImageBitmap(), null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier.size(width = 40.dp, height = 52.dp).clip(shape).background(Color.White),
        )
    } else {
        val (label, color) = when (kind) {
            Kind.Pdf -> "PDF" to IconColors.Red
            Kind.Docx -> "DOC" to IconColors.Blue
            Kind.Markdown -> "MD" to IconColors.Purple
            Kind.Text -> "TXT" to IconColors.Teal
        }
        Box(Modifier.size(width = 40.dp, height = 52.dp).clip(shape).background(color), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
}
