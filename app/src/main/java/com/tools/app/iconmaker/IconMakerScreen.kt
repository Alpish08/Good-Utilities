package com.tools.app.iconmaker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.tools.app.ui.fadingEdges
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.Storage
import com.tools.app.cutout.Cutout
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.GalleryPick
import com.tools.app.ui.Images
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiDialog
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class Backdrop(val label: String) { Keep("Keep"), Colour("Color"), Subject("Cut out") }

private fun prefs(c: Context) = c.getSharedPreferences("iconmaker", Context.MODE_PRIVATE)

/**
 * Icon maker: puts a logo on an icon pack's empty tile (its "folder" icon), so apps the pack doesn't cover can get a
 * matching icon. The logo is fitted inside a size limit and centred on the tile's visible body.
 */
@Composable
fun IconMakerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    val p = remember { prefs(context) }

    val packs by produceState<List<IconPack>?>(null) { value = withContext(Dispatchers.IO) { IconPacks.installed(context) } }
    var pack by remember { mutableStateOf(p.getString("pack", null)) }
    var baseName by remember { mutableStateOf(p.getString("base", null)) }
    var size by remember { mutableFloatStateOf(p.getFloat("size", 0.5f)) }
    var backdrop by remember { mutableStateOf(Backdrop.entries.getOrElse(p.getInt("backdrop", 0)) { Backdrop.Keep }) }
    var white by remember { mutableStateOf(false) }
    var round by remember { mutableStateOf(false) }
    var dx by remember { mutableIntStateOf(0) }
    var dy by remember { mutableIntStateOf(0) }

    var logoSrc by remember { mutableStateOf<Bitmap?>(null) }
    var logoName by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var choosingPack by remember { mutableStateOf(false) }
    var choosingBase by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // First visit (or the remembered pack was uninstalled): take the first pack and its folder tile.
    LaunchedEffect(packs) {
        val list = packs ?: return@LaunchedEffect
        if (list.none { it.pkg == pack }) list.firstOrNull()?.let { pack = it.pkg; baseName = null }
    }
    LaunchedEffect(pack, baseName) {
        val pk = pack ?: return@LaunchedEffect
        val name = baseName ?: withContext(Dispatchers.IO) { IconPacks.defaultBase(context, pk) }
        if (name == null) { base = null; choosingBase = true; return@LaunchedEffect }
        if (baseName == null) baseName = name
        base = withContext(Dispatchers.IO) { IconPacks.load(context, pk, name, minSize = 192) }
        p.edit().putString("pack", pk).putString("base", name).apply()
    }
    // Tidy the logo whenever it or its options change.
    LaunchedEffect(logoSrc, backdrop, white, round) {
        val src = logoSrc ?: return@LaunchedEffect
        try {
            var b = when (backdrop) {
                Backdrop.Keep -> src
                Backdrop.Colour -> withContext(Dispatchers.Default) { IconComposer.removeFlatBackground(src) }
                Backdrop.Subject -> {
                    status = "Getting the model ready…"
                    Cutout.ensureModel(context) { status = "Downloading the model… ${(it * 100).roundToInt()}%" }
                    status = "Cutting out…"
                    Cutout.cut(src)
                }
            }
            b = withContext(Dispatchers.Default) {
                var x = b
                if (white) x = IconComposer.makeWhite(x)
                if (round) IconComposer.circle(x) else IconComposer.trim(x)
            }
            logo = b
            status = null
        } catch (e: Exception) {
            status = e.message ?: "Couldn't process the logo"
        }
    }
    val result by produceState<Bitmap?>(null, base, logo, size, dx, dy) {
        val b = base; val l = logo
        value = if (b == null || l == null) b else withContext(Dispatchers.Default) { IconComposer.compose(b, l, size, dx, dy) }
    }

    val chooser = rememberLauncherForActivityResult(GalleryPick()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { Images.load(context, uri, 1024) }.getOrNull() }
            if (bmp == null) { Toast.makeText(context, "Couldn't open that image", Toast.LENGTH_SHORT).show(); return@launch }
            logoName = displayName(context, uri)
            dx = 0; dy = 0
            logo = null
            logoSrc = bmp
        }
    }
    val choose = { chooser.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    ToolPage(
        "Icon maker", onBack, expandable = logoSrc != null,
        actions = { if (logoSrc != null) ToolbarIcon(OneUiIcons.Gallery, "Choose logo", choose) },
    ) {
        val list = packs
        if (list != null && list.isEmpty()) {
            EmptyState(
                Icons.Rounded.Category, IconColors.Orange, "No icon packs installed",
                "Install an icon pack to build matching icons from its empty tile.", "Choose logo anyway", choose,
            )
            return@ToolPage
        }
        if (logoSrc == null) {
            EmptyState(
                Icons.Rounded.Category, IconColors.Orange, "Make a matching icon",
                "Pick a logo and it's placed on your icon pack's empty tile, ready to choose in your launcher.", "Choose logo", choose,
                modifier = Modifier.weight(1f),
            )
            PackCard(list, pack, baseName, onPack = { choosingPack = true }, onBase = { choosingBase = true })
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Preview(result, status)
            PackCard(list, pack, baseName, onPack = { choosingPack = true }, onBase = { choosingBase = true })
            Column(
                Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card).padding(vertical = 14.dp),
            ) {
                Text("Background", fontSize = 15.sp, color = c.text, modifier = Modifier.padding(horizontal = 18.dp))
                SegmentedControl(
                    Backdrop.entries.map { it.label }, backdrop.ordinal,
                    { backdrop = Backdrop.entries[it]; p.edit().putInt("backdrop", it).apply() },
                    modifier = Modifier.padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 6.dp),
                )
                ListRow("Make white", summary = "For dark one-colour logos", trailing = { OneUiSwitch(white) }, onClick = { white = !white })
                ListRow("Round into a circle", summary = "Keeps the whole logo, rounded", trailing = { OneUiSwitch(round) }, onClick = { round = !round })
                Row(Modifier.padding(horizontal = 18.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Size", fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
                    Text("${(size * 100).roundToInt()}%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                }
                OneUiSlider(
                    size, { size = (it * 100).roundToInt() / 100f }, Modifier.padding(horizontal = 18.dp).padding(top = 4.dp),
                    valueRange = 0.3f..0.85f, onValueChangeFinished = { p.edit().putFloat("size", size).apply() },
                )
                Row(
                    Modifier.padding(horizontal = 18.dp).padding(top = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Position", fontSize = 15.sp, color = c.text, modifier = Modifier.weight(1f))
                    Nudge(Icons.Rounded.KeyboardArrowLeft, "Left") { dx-- }
                    Nudge(Icons.Rounded.KeyboardArrowUp, "Up") { dy-- }
                    Nudge(Icons.Rounded.KeyboardArrowDown, "Down") { dy++ }
                    Nudge(Icons.Rounded.KeyboardArrowRight, "Right") { dx++ }
                    Text(
                        "Reset", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = if (dx != 0 || dy != 0) c.accent else c.textSecondary,
                        modifier = Modifier.clip(CircleShape).clickable { dx = 0; dy = 0 }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            PillButton(
                if (saving) "Saving…" else "Save icon",
                enabled = result != null && logo != null && !saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = CardMargin + 8.dp, vertical = 14.dp),
                onClick = {
                    val out = result ?: return@PillButton
                    saving = true
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            runCatching {
                                Storage.save(context, Storage.Folder.Icons, null, "${logoName.ifBlank { "icon" }} icon.png") {
                                    out.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            }
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            saved.fold({ "Saved to ${Storage.label(Storage.Folder.Icons)}/$it" }, { it.message ?: "Couldn't save the icon" }),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }

    if (choosingPack) OneUiDialog("Icon pack", { choosingPack = false }, buttons = listOf("Cancel" to { choosingPack = false })) {
        packs.orEmpty().forEach { ip ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { if (ip.pkg != pack) { pack = ip.pkg; baseName = null }; choosingPack = false }
                    .padding(horizontal = 6.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.tools.app.ui.RadioDot(ip.pkg == pack)
                Text(ip.label, fontSize = 16.sp, color = c.text, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
    val pk = pack
    if (choosingBase && pk != null) BasePicker(pk, baseName, onPick = { baseName = it; choosingBase = false }, onDismiss = { choosingBase = false })
}

@Composable
private fun PackCard(packs: List<IconPack>?, pack: String?, baseName: String?, onPack: () -> Unit, onBase: () -> Unit) {
    val c = OneUi.colors
    Column(Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape).background(c.card)) {
        ListRow("Icon pack", summary = packs?.firstOrNull { it.pkg == pack }?.label ?: "Loading…", onClick = onPack)
        ListRow("Base icon", summary = baseName?.let { "\"$it\"" } ?: "Choose the pack's empty tile", onClick = onBase)
    }
}

/** The icon large, and at Home screen size on a dark and a light backdrop. */
@Composable
private fun Preview(icon: Bitmap?, status: String?) {
    val c = OneUi.colors
    Column(
        Modifier.padding(horizontal = CardMargin, vertical = 6.dp).fillMaxWidth().clip(OneUi.CardShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B3550), Color(0xFF151A26)))).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                val img = remember(icon) { icon.asImageBitmap() }
                Image(img, null, Modifier.size(170.dp), filterQuality = FilterQuality.High)
            }
        }
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                val img = remember(icon) { icon.asImageBitmap() }
                Image(img, null, Modifier.size(58.dp), filterQuality = FilterQuality.High)
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFE9E6E1)), contentAlignment = Alignment.Center) {
                    Image(img, null, Modifier.size(58.dp), filterQuality = FilterQuality.High)
                }
            }
        }
        Text(status ?: "Preview at Home screen size", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Nudge(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = OneUi.colors
    Box(Modifier.size(38.dp).clip(CircleShape).background(c.tonal).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = c.text, modifier = Modifier.size(24.dp))
    }
}

/** Pick the pack's tile to build on: searchable, with thumbnails. "folder" matches come first. */
@Composable
private fun BasePicker(pkg: String, current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val names by produceState<List<String>?>(null, pkg) { value = withContext(Dispatchers.IO) { IconPacks.drawableNames(context, pkg) } }
    val thumbs = remember(pkg) { android.util.LruCache<String, Bitmap>(120) }
    OneUiDialog("Base icon", onDismiss, buttons = listOf("Cancel" to onDismiss)) {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(com.tools.app.ui.popupFieldFill(c.isDark))
                .border(0.6.dp, com.tools.app.ui.pillEdge(c.isDark), CircleShape)
                .clickable(interactionSource = null, indication = null) { focus.requestFocus() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(OneUiIcons.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            Box(Modifier.padding(start = 10.dp).weight(1f)) {
                if (query.isEmpty()) Text("Search the pack (e.g. folder)", fontSize = 16.sp, color = c.textSecondary)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = com.tools.app.ui.theme.SamsungFont, fontSize = 16.sp, color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
        val all = names
        val shown = remember(all, query) {
            val q = query.trim().lowercase()
            (all ?: emptyList())
                .filter { q.isEmpty() || it.lowercase().contains(q) }
                .sortedByDescending { if (q.isEmpty()) it.contains("folder") else it.lowercase() == q }
                .take(120)
        }
        if (all == null) Text("Loading…", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
        else if (shown.isEmpty()) Text("No icons match", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(vertical = 20.dp))
        else {
            val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.padding(top = 12.dp).heightIn(max = 380.dp).fadingEdges(grid), state = grid) {
            items(shown, key = { it }) { name ->
                val thumb by produceState(thumbs.get(name), name) {
                    if (value == null) value = withContext(Dispatchers.IO) { IconPacks.load(context, pkg, name)?.let { b ->
                        android.graphics.Bitmap.createScaledBitmap(b, 144, (144f * b.height / b.width).toInt().coerceAtLeast(1), true)
                    } }?.also { thumbs.put(name, it) }
                }
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (name == current) c.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onPick(name) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        thumb?.let { Image(remember(it) { it.asImageBitmap() }, null, Modifier.size(56.dp)) }
                    }
                    Text(name, fontSize = 11.sp, color = c.textSecondary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    val n = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    return n.substringBeforeLast('.').ifBlank { "icon" }
}
