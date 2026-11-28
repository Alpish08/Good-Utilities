package com.tools.app.reader

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.convert.Docx
import com.tools.app.convert.PdfBuilder
import com.tools.app.ui.EmptyState
import com.tools.app.ui.ToolPage
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Entry point for documents opened from other apps (My Files, Gmail, Chrome…) and from the reader home. */
class ReaderActivity : ComponentActivity() {
    private var uri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        take(intent)
        setContent { OneUiTheme(landscapeHidesStatusBar = false) { ReaderRoot(uri, onClose = { finish() }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    private fun take(i: Intent?) {
        val u = i?.data ?: i?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (u != null && u.scheme == "content" && (i?.flags ?: 0) and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        uri = u
    }
}

private sealed interface Stage {
    class Loading(val message: String) : Stage
    class Pdf(val doc: PdfDoc, val start: Position?) : Stage
    class Txt(val info: DocInfo, val blocks: List<TBlock>, val start: Position?) : Stage
    class Err(val title: String, val summary: String) : Stage
}

@Composable
private fun ReaderRoot(uri: Uri?, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(OneUi.colors.background)) {
        if (uri == null) ErrorView(t(R.string.reader_no_document), t(R.string.reader_there_s_nothing_to_show_open_a_pdf_wor), "One Reader", onClose)
        else key(uri) { DocumentHost(uri, onClose) }
    }
}

@Composable
private fun DocumentHost(uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { ReaderStore.Settings(ctx) }
    var stage by remember { mutableStateOf<Stage>(Stage.Loading(t(R.string.audio_opening))) }
    var prompt by remember { mutableStateOf<Pair<Boolean, CompletableDeferred<String?>>?>(null) }
    var name by remember { mutableStateOf("One Reader") }

    LaunchedEffect(uri) {
        try {
            withContext(Dispatchers.IO) { ReaderStore.trimCache(ctx) }
            val info = withContext(Dispatchers.IO) { DocInfo.resolve(ctx, uri) }
            name = info.name
            val start = ReaderStore.position(ctx, info.key)
            when (info.kind) {
                Kind.Pdf, Kind.Docx -> {
                    var source = uri
                    if (info.kind == Kind.Docx) {
                        stage = Stage.Loading(t(R.string.reader_preparing_document))
                        source = Uri.fromFile(withContext(Dispatchers.IO) { convertDocx(ctx, info) })
                    }
                    val doc = PdfDoc.open(ctx, source, info) { wrong ->
                        val d = CompletableDeferred<String?>()
                        prompt = wrong to d
                        try { d.await() } finally { prompt = null }
                    }
                    ReaderStore.touch(ctx, info, doc.pageCount, start?.page ?: 0)
                    stage = Stage.Pdf(doc, start)
                }
                Kind.Text, Kind.Markdown -> {
                    stage = Stage.Loading(t(R.string.audio_opening))
                    val blocks = withContext(Dispatchers.Default) { loadText(ctx, info) }
                    ReaderStore.touch(ctx, info, 0, start?.page ?: 0)
                    stage = Stage.Txt(info, blocks, start)
                }
            }
        } catch (e: PasswordCancelled) {
            onClose()
        } catch (e: ReaderError) {
            stage = Stage.Err(e.title, e.summary)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            stage = Stage.Err(t(R.string.reader_file_too_large), t(R.string.reader_this_document_is_too_big_to_open_on_th))
        } catch (e: Throwable) {
            stage = Stage.Err(t(R.string.reader_can_t_open_this_file), t(R.string.reader_the_file_couldn_t_be_read_it_may_be_da))
        }
    }

    DisposableEffect(stage) { val s = stage; onDispose { (s as? Stage.Pdf)?.doc?.close() } }

    when (val s = stage) {
        is Stage.Loading -> ToolPage("", onClose) {
            Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                com.tools.app.ui.OneUiSpinner(color = OneUi.colors.accent, modifier = Modifier.size(40.dp))
                Text(s.message, fontSize = 15.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 18.dp))
            }
        }
        is Stage.Pdf -> PdfViewer(
            s.doc, settings, s.start,
            onPosition = { ReaderStore.savePosition(ctx, s.doc.info, s.doc.pageCount, it) },
            onBack = onClose,
        )
        is Stage.Txt -> TextViewer(
            s.info, s.blocks, settings, s.start,
            onPosition = { ReaderStore.savePosition(ctx, s.info, 0, it) }, onBack = onClose,
        )
        is Stage.Err -> ErrorView(s.title, s.summary, name, onClose)
    }

    prompt?.let { (wrong, d) -> PasswordDialog(name, wrong) { d.complete(it) } }
}

@Composable
private fun ErrorView(title: String, summary: String, name: String, onClose: () -> Unit) {
    ToolPage(name, onClose) {
        EmptyState(Icons.Rounded.ErrorOutline, title, summary, t(R.string.ui_close), onClose)
    }
}

private fun loadText(ctx: Context, info: DocInfo): List<TBlock> {
    val limit = 24 * 1024 * 1024
    val bytes = try {
        (if (info.uri.scheme == "file") File(info.uri.path ?: "").inputStream() else ctx.contentResolver.openInputStream(info.uri))
            ?.use { it.readNBytes(limit) } ?: throw IOException("no stream")
    } catch (e: SecurityException) {
        throw ReaderError(t(R.string.reader_no_permission), t(R.string.reader_one_reader_isn_t_allowed_to_open_this))
    } catch (e: IOException) {
        throw ReaderError(t(R.string.reader_can_t_read_this_file), t(R.string.reader_couldn_t_be_read_it_may_have_been_move, info.name))
    }
    val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
    return parseText(String(bytes, start, bytes.size - start, Charsets.UTF_8), info.kind == Kind.Markdown)
}

/** DOCX → cached PDF using the converter's layout engine. Cached by URI + size, so reopening is instant. */
private fun convertDocx(ctx: Context, info: DocInfo): File {
    val dir = ReaderStore.cacheDir(ctx)
    val out = File(dir, "docx_${info.key}.pdf")
    if (out.length() > 0) { out.setLastModified(System.currentTimeMillis()); return out }
    val src = File(dir, "docx_${info.key}.src")
    val part = File(dir, "docx_${info.key}.part")
    try {
        val input = (if (info.uri.scheme == "file") File(info.uri.path ?: "").inputStream() else ctx.contentResolver.openInputStream(info.uri))
            ?: throw IOException("no stream")
        input.use { i -> src.outputStream().use { o -> i.copyTo(o) } }
        val blocks = Docx.parse(src)
        part.outputStream().buffered().use { PdfBuilder().render(blocks, it) { } }
        if (!part.renameTo(out)) throw IOException("rename failed")
    } catch (e: Docx.Invalid) {
        throw ReaderError(t(R.string.reader_can_t_open_this_file), t(R.string.reader_isn_t_a_valid_word_document, info.name))
    } catch (e: SecurityException) {
        throw ReaderError(t(R.string.reader_no_permission), t(R.string.reader_one_reader_isn_t_allowed_to_open_this))
    } catch (e: IOException) {
        throw ReaderError(t(R.string.reader_can_t_open_this_file), t(R.string.reader_couldn_t_be_converted_for_viewing, info.name))
    } finally {
        src.delete(); part.delete()
    }
    return out
}
