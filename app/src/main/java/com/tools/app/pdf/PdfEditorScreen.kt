package com.tools.app.pdf

import com.tools.app.ui.EditorSplit
import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.Icons
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.tools.app.Storage
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OverflowMenu
import com.tools.app.ui.PillButton
import com.tools.app.ui.RoundCheck
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.OneUiEasing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PdfEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = PdfSession
    val c = OneUi.colors
    val export by s.export.collectAsState()
    var editing by remember { mutableStateOf<Long?>(null) }
    var pendingImages by remember { mutableStateOf<List<android.net.Uri>?>(null) }
    var fitA4 by remember { mutableStateOf(true) }
    var securityOpen by remember { mutableStateOf(false) }

    val openOne = rememberLauncherForActivityResult(com.tools.app.ui.FilesPick()) { u -> if (u != null) s.open(context, listOf(u), append = false) }
    val openMany = rememberLauncherForActivityResult(com.tools.app.ui.FilesPickMultiple()) { l -> s.open(context, l, append = true) }
    val pickImages = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPickMultiple()) { l -> if (l.isNotEmpty()) pendingImages = l }
    val pdfMime = arrayOf("application/pdf")

    BackHandler(editing != null) { editing = null }

    // Hand a prepared file to the share sheet once.
    LaunchedEffect(export) {
        val d = export as? ExportState.Done ?: return@LaunchedEffect
        val f = d.share ?: return@LaunchedEffect
        if (d.consumed) return@LaunchedEffect
        d.consumed = true
        val uri = FileProvider.getUriForFile(context, "com.tools.app.files", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val editItem = editing?.let { id -> s.pages.firstOrNull { it.id == id } }
    if (editItem != null) {
        val idx = s.pages.indexOf(editItem)
        androidx.compose.runtime.key(editItem.id) {
            PdfAnnotateScreen(
                editItem, idx + 1, s.pages.size,
                onCancel = { editing = null },
                onDone = { s.setAnnots(editItem.id, it); editing = null },
                onGo = { annots, delta ->
                    s.setAnnots(editItem.id, annots)
                    s.pages.getOrNull(idx + delta)?.let { editing = it.id }
                },
            )
        }
        return
    }

    val running = export is ExportState.Running
    val loaded = s.pages.isNotEmpty()
    ToolPage(
        "PDF editor", onBack, expandable = loaded,
        actions = {
            if (loaded) {
                ToolbarIcon(OneUiIcons.Share, "Share") { s.export(context, s.pages.toList(), "edited", share = true) }
                OverflowMenu(listOf(
                    "Open another PDF" to { openOne.launch(pdfMime) },
                    "Close PDF" to { s.close() },
                ))
            }
        },
    ) {
        if (!loaded) {
            Column(Modifier.weight(1f)) {
                EmptyState(
                    Icons.Rounded.PictureAsPdf, IconColors.Red, "Edit or create a PDF",
                    "Reorder, rotate, split and merge pages, sign them, mark them up and add a password. Or turn photos into a PDF and draw on them.",
                    if (s.busy != null) "Opening…" else "Open PDF",
                    { if (s.busy == null) openOne.launch(pdfMime) },
                    secondary = "Create from images" to { if (s.busy == null) pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
            s.notice?.let { Text(it.text, color = if (it.error) c.error else c.success, fontSize = 14.sp, modifier = Modifier.padding(24.dp, 8.dp)) }
        } else {
            val selected = s.selected
            EditorSplit(picture = { m ->
            LazyVerticalGrid(
                GridCells.Fixed(3),
                m,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(s.pages, key = { _, p -> p.id }) { i, p ->
                    PageCell(p, i + 1, p.id in selected, Modifier.animateItem()) { s.toggle(p.id) }
                }
            }
            }) {
            ActionCard(
                busy = s.busy, running = running,
                onAddPdfs = { openMany.launch(pdfMime) },
                onAddImages = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onSecurity = { securityOpen = true },
                onEdit = { s.selected.firstOrNull()?.let { editing = it } },
                onExtract = { s.export(context, s.pages.filter { it.id in s.selected }, "extract", share = false) },
                onSave = { s.export(context, s.pages.toList(), "edited", share = false) },
                onShare = { s.export(context, s.pages.toList(), "edited", share = true) },
                export = export,
                onGrantAccess = { context.startActivity(Storage.accessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            )
            }
        }
    }

    // ----- dialogs -----
    s.passwordPrompt?.let { prompt ->
        var pw by remember(prompt) { mutableStateOf("") }
        OneUiDialog(
            "Password needed", onDismiss = { s.answerPassword(null) }, dismissible = false,
            buttons = listOf("Cancel" to { s.answerPassword(null) }, "Open" to { s.answerPassword(pw) }),
        ) {
            Text("\"${prompt.name}\" is protected. Enter its password to open it.", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
            DialogField(pw, { pw = it }, "Password", password = true)
            if (prompt.wrong) Text("That password didn't work. Try again.", fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 8.dp))
        }
    }

    pendingImages?.let { uris ->
        OneUiDialog(
            "Add ${uris.size} ${if (uris.size == 1) "image" else "images"}", onDismiss = { pendingImages = null },
            buttons = listOf("Cancel" to { pendingImages = null }, "Add" to { s.addImages(context, uris, fitA4); pendingImages = null }),
        ) {
            Text("Each image becomes its own page.", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp))
            SegmentedControl(listOf("Fit to A4", "Image size"), if (fitA4) 0 else 1, { fitA4 = it == 0 })
        }
    }

    if (securityOpen) {
        var pw by remember { mutableStateOf("") }
        var again by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        OneUiDialog(
            "Password", onDismiss = { securityOpen = false },
            buttons = buildList {
                add("Cancel" to { securityOpen = false })
                if (s.newPassword != null) add("Remove" to { s.newPassword = null; securityOpen = false })
                add("Set" to {
                    when {
                        pw.length < 4 -> error = "Use at least 4 characters."
                        pw != again -> error = "The passwords don't match."
                        else -> { s.newPassword = pw; securityOpen = false }
                    }
                })
            },
        ) {
            Text(
                buildString {
                    append("Encrypts the saved copy with AES-256.")
                    if (s.hadPassword) append(" The original's password is already removed from the copy; leave this unset to keep it that way.")
                    if (s.newPassword != null) append("\nA password is set for the next save.")
                },
                fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(bottom = 12.dp),
            )
            DialogField(pw, { pw = it; error = null }, "New password", password = true)
            Box(Modifier.padding(top = 8.dp)) { DialogField(again, { again = it; error = null }, "Repeat password", password = true) }
            error?.let { Text(it, fontSize = 13.sp, color = c.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun PageCell(p: PageItem, number: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = OneUi.colors
    val bmp by produceState<Bitmap?>(Thumbs.get(p.src, p.index), p.src.id, p.index) {
        if (value == null) value = Thumbs.load(p.src, p.index)
    }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(320, easing = OneUiEasing), label = "thumbFade")
    val angle by animateFloatAsState(p.rot.toFloat(), tween(300, easing = OneUiEasing), label = "thumbRot")
    val borderColor by androidx.compose.animation.animateColorAsState(if (selected) c.accent else androidx.compose.ui.graphics.Color.Transparent, tween(180), label = "thumbBorder")
    val checkScale by animateFloatAsState(if (selected) 1f else 0.001f, tween(220, easing = OneUiEasing), label = "checkScale")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.aspectRatio(0.72f).clip(shape).background(c.card).border(2.5.dp, borderColor, shape).clickable(onClick = onClick),
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val w = img.width.toFloat(); val h = img.height.toFloat()
                val r = Math.toRadians(angle.toDouble())
                val cw = (w * abs(cos(r)) + h * abs(sin(r))).toFloat()
                val ch = (w * abs(sin(r)) + h * abs(cos(r))).toFloat()
                val sc = min(size.width / cw, size.height / ch)
                withTransform({
                    translate(size.width / 2, size.height / 2)
                    rotate(angle, Offset.Zero)
                    scale(sc, sc, Offset.Zero)
                }) { drawImage(img, Offset(-w / 2, -h / 2), alpha = alpha) }
            }
        }
        Text(
            "$number", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).clip(CircleShape).background(c.tonal).padding(horizontal = 9.dp, vertical = 2.dp),
        )
        if (p.annots.isNotEmpty()) Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(8.dp).background(c.accent, CircleShape))
        Box(Modifier.align(Alignment.TopEnd).padding(7.dp).graphicsLayer { scaleX = 0.001f + checkScale; scaleY = 0.001f + checkScale; this.alpha = checkScale }) {
            RoundCheck(true)
        }
        if (!selected) Box(Modifier.align(Alignment.TopEnd).padding(7.dp)) { RoundCheck(false) }
    }
}

@Composable
private fun ActionCard(
    busy: String?, running: Boolean, export: ExportState,
    onAddPdfs: () -> Unit, onAddImages: () -> Unit, onSecurity: () -> Unit,
    onEdit: () -> Unit, onExtract: () -> Unit, onSave: () -> Unit, onShare: () -> Unit, onGrantAccess: () -> Unit,
) {
    val c = OneUi.colors
    val s = PdfSession
    val n = s.selected.size
    val idle = !running && busy == null
    val on = n > 0 && idle
    Column(
        Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (n == 0) "Tap pages to select" else "$n selected", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text("${s.pages.size} ${if (s.pages.size == 1) "page" else "pages"}${if (s.newPassword != null) " · password on save" else ""}", fontSize = 13.sp, color = c.textSecondary)
            }
            Text(
                if (n == s.pages.size) "Deselect all" else "Select all", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                modifier = Modifier.clip(CircleShape).clickable { s.selectAll() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Rotate left", { s.rotate(-90) }, tonal = true, enabled = on, height = 40.dp)
            PillButton("Rotate right", { s.rotate(90) }, tonal = true, enabled = on, height = 40.dp)
            PillButton("Earlier", { s.moveSelected(true) }, tonal = true, enabled = on, height = 40.dp, icon = OneUiIcons.ChevronUp)
            PillButton("Later", { s.moveSelected(false) }, tonal = true, enabled = on, height = 40.dp, icon = OneUiIcons.ChevronDown)
            PillButton("Edit page", onEdit, tonal = true, enabled = n == 1 && idle, height = 40.dp)
            PillButton("Extract", onExtract, tonal = true, enabled = on, height = 40.dp)
            PillButton("Delete", { s.deleteSelected() }, tonal = true, enabled = on, height = 40.dp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Add PDFs", onAddPdfs, tonal = true, enabled = idle, height = 40.dp, icon = OneUiIcons.Add)
            PillButton("Add images", onAddImages, tonal = true, enabled = idle, height = 40.dp, icon = OneUiIcons.Image)
            PillButton(if (s.newPassword != null) "Password: on" else "Password", onSecurity, tonal = true, enabled = idle, height = 40.dp, icon = OneUiIcons.Lock)
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            val note = s.notice
            when {
                busy != null -> Text(busy, fontSize = 14.sp, color = c.textSecondary)
                export is ExportState.Running -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${export.label} ${(export.progress * 100).toInt()}%", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
                        Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                            modifier = Modifier.clip(CircleShape).clickable { s.cancelExport() }.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    ThinProgress(export.progress, Modifier.padding(top = 8.dp))
                }
                export is ExportState.Done -> Text(export.message, fontSize = 14.sp, color = c.success)
                export is ExportState.Failed -> {
                    Text(export.message, fontSize = 14.sp, color = c.error)
                    if (export.needsAccess) Text("Allow access", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent,
                        modifier = Modifier.padding(top = 4.dp).clip(CircleShape).clickable(onClick = onGrantAccess).padding(vertical = 4.dp))
                }
                note != null -> Text(note.text, fontSize = 14.sp, color = if (note.error) c.error else c.success)
            }
            Row(Modifier.padding(top = if (busy != null || export !is ExportState.Idle || note != null) 10.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(if (running) "Working…" else "Save a copy", onSave, Modifier.weight(1f), enabled = idle)
                PillButton("Share", onShare, tonal = true, enabled = idle, icon = OneUiIcons.Share)
            }
        }
    }
}
