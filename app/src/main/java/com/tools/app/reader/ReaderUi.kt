package com.tools.app.reader

import com.tools.app.R
import com.tools.app.ui.t
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.blur.hazeBlur
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import kotlin.concurrent.thread

internal val BarHeight = 64.dp

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** One UI toolbar (back, title, actions), drawn translucent so pages scroll under it. */
@Composable
internal fun ReaderBar(
    title: String, onBack: () -> Unit, modifier: Modifier = Modifier, alpha: Float = 0.94f,
    /** PDF pages: the bar turns a see-through grey and blurs the pages scrolling under it. */
    haze: dev.chrisbanes.haze.HazeState? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = OneUi.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fill = if (haze != null) frostedBar(c.isDark) else c.background.copy(alpha = alpha)
    Row(
        modifier.fillMaxWidth()
            .then(haze?.let { blurBehind(it, c.background) } ?: Modifier)
            .background(fill).padding(top = top).height(BarHeight).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(OneUiIcons.ArrowBack, t(R.string.ui_navigate_up), tint = c.text, modifier = Modifier.size(24.dp))
        }
        Text(
            title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 2.dp, end = 4.dp),
        )
        actions()
    }
}

/** Search toolbar: query field, "3 of 17" and previous / next. */
@Composable
internal fun SearchBar(
    query: String, onQuery: (String) -> Unit, status: String,
    onPrev: () -> Unit, onNext: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, alpha: Float = 0.94f,
) {
    val c = OneUi.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        modifier.fillMaxWidth().background(c.background.copy(alpha = alpha)).padding(top = top).height(BarHeight).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(OneUiIcons.ArrowBack, t(R.string.reader_close_search), tint = c.text, modifier = Modifier.size(24.dp))
        }
        Box(
            Modifier.weight(1f).height(44.dp).clip(CircleShape).background(c.tonal).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                query, onQuery, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(t(R.string.home_search), fontSize = 16.sp, color = c.textSecondary)
                            inner()
                        }
                        if (status.isNotEmpty()) Text(status, fontSize = 13.sp, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                },
            )
        }
        ToolbarIcon(OneUiIcons.ChevronUp, t(R.string.reader_previous_result), onPrev)
        ToolbarIcon(OneUiIcons.ChevronDown, t(R.string.reader_next_result), onNext)
    }
}

/** Rounded single-line input used in dialogs. */
@Composable
internal fun OneUiField(
    value: String, onChange: (String) -> Unit, hint: String,
    keyboardType: KeyboardType = KeyboardType.Text, password: Boolean = false, onDone: () -> Unit = {}, error: Boolean = false,
) {
    val c = OneUi.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(c.tonal).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = if (error) c.error else c.text, fontSize = 17.sp),
            cursorBrush = SolidColor(c.accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(hint, fontSize = 17.sp, color = c.textSecondary); inner() }
            },
        )
    }
}

@Composable
internal fun PasswordDialog(name: String, wrong: Boolean, onSubmit: (String?) -> Unit) {
    val c = OneUi.colors
    var text = remember { androidx.compose.runtime.mutableStateOf("") }
    OneUiDialog(
        t(R.string.reader_password_required), onDismiss = { onSubmit(null) }, dismissible = false,
        buttons = listOf(t(R.string.ui_cancel) to { onSubmit(null) }, "OK" to { if (text.value.isNotEmpty()) onSubmit(text.value) }),
    ) {
        Text(t(R.string.reader_is_protected_enter_its_password_to_ope, name), fontSize = 14.5.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 14.dp))
        OneUiField(text.value, { text.value = it }, t(R.string.reader_password), password = true, error = wrong, onDone = { if (text.value.isNotEmpty()) onSubmit(text.value) })
        if (wrong) Text(t(R.string.reader_wrong_password_try_again), fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 8.dp, start = 6.dp))
    }
}

@Composable
internal fun InfoDialog(info: DocInfo, pages: Int?, meta: PdfMeta?, onDismiss: () -> Unit) {
    val c = OneUi.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    OneUiDialog(t(R.string.reader_file_info), onDismiss, listOf(t(R.string.ui_close) to onDismiss)) {
        @Composable fun row(k: String, v: String?) {
            if (v.isNullOrBlank()) return
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(k, fontSize = 13.sp, color = c.textSecondary)
                Text(v, fontSize = 15.5.sp, color = c.text, fontWeight = FontWeight.SemiBold)
            }
        }
        row(t(R.string.reader_name), info.name)
        row(t(R.string.reader_size), if (info.size >= 0) Formatter.formatFileSize(ctx, info.size) else null)
        row(t(R.string.video_type), when (info.kind) { Kind.Pdf -> t(R.string.reader_pdf_document); Kind.Docx -> t(R.string.reader_word_document); Kind.Markdown -> t(R.string.reader_markdown); Kind.Text -> t(R.string.reader_plain_text) })
        row(t(R.string.reader_pages), pages?.toString())
        row(t(R.string.reader_title), meta?.title)
        row(t(R.string.reader_author), meta?.author)
    }
}

fun shareDocument(ctx: Context, info: DocInfo) {
    try {
        val uri = contentUri(ctx, info.uri)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = info.kind.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(info.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, t(R.string.video_share)))
    } catch (e: Exception) {
        Toast.makeText(ctx, t(R.string.reader_can_t_share_this_file), Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(ctx: Context, label: String, text: String) {
    ctx.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, t(R.string.reader_copied), Toast.LENGTH_SHORT).show()
}

/** Prints the PDF by streaming its bytes to the print framework. */
fun printPdf(ctx: Context, doc: PdfDoc) {
    val pm = ctx.getSystemService<PrintManager>() ?: return
    val name = doc.info.name
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(doc.pageCount).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
            thread(name = "print-write") {
                try {
                    doc.stream().use { i -> ParcelFileDescriptor.AutoCloseOutputStream(dest).use { o -> i.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }
    }
    pm.print(name, adapter, PrintAttributes.Builder().build())
}

/** The PDF reader's bars: a neutral grey, see-through enough for the blur behind it to show. */
internal fun frostedBar(dark: Boolean) = if (dark) Color(0xFF2B2B2D).copy(alpha = 0.6f) else Color(0xFFF1F1F3).copy(alpha = 0.68f)

/** Blurs what [haze] captured (the pages) behind this bar, over [backing] so nothing sharp shows through. */
internal fun blurBehind(haze: dev.chrisbanes.haze.HazeState, backing: Color, radius: androidx.compose.ui.unit.Dp = 24.dp): Modifier = Modifier.hazeBlur(
    dev.chrisbanes.haze.HazeInput.Sources(haze),
    dev.chrisbanes.haze.blur.HazeBlurStyle { blurRadius(radius); noiseFactor(0f); backgroundColor(backing) },
)
