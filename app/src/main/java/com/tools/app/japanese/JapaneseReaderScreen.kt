package com.tools.app.japanese

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon as AIcon
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.tools.app.R
import com.tools.app.ui.CardDivider
import com.tools.app.ui.ColorIcon
import com.tools.app.ui.EmptyState
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiIcons
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.OneUiSwitch
import com.tools.app.ui.PillButton
import com.tools.app.ui.RoundCard
import com.tools.app.ui.SegmentedControl
import com.tools.app.ui.ThinProgress
import com.tools.app.ui.ToolPage
import com.tools.app.ui.card
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.sectionLabel
import com.tools.app.ui.theme.IconColors
import com.tools.app.ui.theme.OneUi
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Main page of the Japanese reader: capture options, tile help, model status and "My words". */
@Composable
fun JapaneseReaderScreen(onBack: () -> Unit, notice: String? = null) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        JpPrefs.init(ctx); WordStore.init(ctx); Translate.refresh(); DictJobs.refreshInfo()
    }
    var practicing by remember { mutableStateOf(false) }
    BackHandler(practicing) { practicing = false }
    if (practicing) { Flashcards(onClose = { practicing = false }); return }

    val words by WordStore.words.collectAsState()
    val dictVersion by DictJobs.version.collectAsState()
    val dictState by DictJobs.state.collectAsState()
    val assets by DictJobs.assets.collectAsState()
    val model by Translate.state.collectAsState()
    val stats = remember(dictVersion, dictState) { Jmdict.stats(ctx) }
    var serviceOn by remember { mutableStateOf(JapaneseCapture.serviceEnabled(ctx)) }
    LaunchedEffect(Unit) { serviceOn = JapaneseCapture.serviceEnabled(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) JapaneseReaderActivity.open(ctx, uri)
    }
    val photoFile = remember { File(ctx.cacheDir, "japanese/photo.jpg").also { it.parentFile?.mkdirs() } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && photoFile.length() > 0) JapaneseReaderActivity.openFile(ctx, photoFile)
    }

    OneUiScaffold(title = "Japanese reader", onBack = onBack) {
        if (notice != null) card("notice") {
            Text(notice, fontSize = 14.5.sp, lineHeight = 21.sp, color = OneUi.colors.text, modifier = Modifier.padding(18.dp))
        }

        sectionLabel("Read something")
        card("capture") {
            ListRow(
                "Choose image", summary = "A screenshot or picture with Japanese text",
                leading = { ColorIcon(OneUiIcons.Gallery, IconColors.Purple) },
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            CardDivider(62.dp)
            ListRow(
                "Take photo", summary = "A sign, a page, a game screen",
                leading = { ColorIcon(Icons.Rounded.CameraAlt, IconColors.Orange) },
                onClick = {
                    photoFile.delete()
                    camera.launch(FileProvider.getUriForFile(ctx, "com.tools.app.files", photoFile))
                },
            )
        }
        description("You can also share an image from any app and pick Read Japanese.")

        sectionLabel("Quick Settings tile")
        card("tile") {
            ListRow(
                "Read the screen you are on",
                summary = "Swipe down, tap Read Japanese. Works over Mihon, games, video and anything else, then tap a line for readings and meanings.",
                leading = { ColorIcon(Icons.Rounded.Translate, IconColors.Red) },
            )
            CardDivider(62.dp)
            ListRow(
                if (serviceOn) "Screen capture is ready" else "App lock's accessibility service is off",
                summary = if (serviceOn) "Uses the same accessibility service as App lock. It captures the screen only when you tap the tile."
                else "The tile needs it to take the screenshot. After a reinstall, run ~/enable-applock.sh on your computer.",
                summaryColor = if (serviceOn) null else OneUi.colors.error,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    PillButton("Add tile to Quick Settings", {
                        runCatching {
                            ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                ComponentName(ctx, JapaneseTileService::class.java), ctx.getString(R.string.read_japanese),
                                AIcon.createWithResource(ctx, R.drawable.ic_qs_japanese), ContextCompat.getMainExecutor(ctx),
                            ) { }
                        }
                    }, tonal = true, height = 44.dp)
                }
            }
        }

        sectionLabel("Dictionary")
        card("dict") {
            val s = dictState
            when {
                s is DictState.Working -> Column(Modifier.padding(18.dp)) {
                    Text("Preparing the dictionary…", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(s.progress)
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%  Downloading JMdict and indexing it for offline use", fontSize = 13.sp, color = OneUi.colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text("Cancel", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent, modifier = Modifier.clickable { DictJobs.cancel() }.padding(vertical = 4.dp))
                }
                s is DictState.Checking -> ListRow("Checking the latest JMdict release…")
                stats != null -> {
                    ListRow(
                        "English dictionary installed", summary = "${"%,d".format(stats.first)} entries · ${formatMb(stats.second)} on this phone\nJMdict (EDRDG), CC BY-SA 4.0",
                        trailing = { Icon(OneUiIcons.CheckCircle, null, tint = OneUi.colors.success, modifier = Modifier.size(24.dp)) },
                    )
                    CardDivider()
                    ListRow("Remove dictionary", summary = "Frees the space; you can download it again", onClick = { DictJobs.delete(ctx) })
                }
                else -> Column(Modifier.padding(18.dp)) {
                    Text("Offline dictionary", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text("Meanings and parts of speech for every word, with no internet needed after this download. JMdict (EDRDG), CC BY-SA 4.0.", fontSize = 13.sp, lineHeight = 18.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                    SegmentedControl(
                        listOf("Everything", "Common only"), if (JpPrefs.variant == DictVariant.Full) 0 else 1,
                        { JpPrefs.changeVariant(if (it == 0) DictVariant.Full else DictVariant.Common) },
                    )
                    Spacer(Modifier.height(12.dp))
                    if (s is DictState.Failed) Text(s.message, fontSize = 13.sp, color = OneUi.colors.error, modifier = Modifier.padding(bottom = 8.dp))
                    val size = assets[JpPrefs.variant]?.size ?: if (JpPrefs.variant == DictVariant.Full) 11_500_000L else 1_450_000L
                    PillButton("Download dictionary (~${formatMb(size)})", { DictJobs.start(ctx, JpPrefs.variant) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        sectionLabel("Translation")
        card("translate") {
            when (val m = model) {
                is ModelState.Ready -> {
                    ListRow(
                        "Japanese to English model ready", summary = "Sentences are translated on this phone",
                        trailing = { Icon(OneUiIcons.CheckCircle, null, tint = OneUi.colors.success, modifier = Modifier.size(24.dp)) },
                    )
                    CardDivider()
                    ListRow("Remove model", summary = "About 30 MB", onClick = { Translate.delete() })
                }
                is ModelState.Downloading -> Column(Modifier.padding(18.dp)) {
                    Text("Downloading the translation model…", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = OneUi.colors.accent, trackColor = OneUi.colors.sliderTrack)
                    Spacer(Modifier.height(6.dp))
                    Text(if (m.wifiOnly) "About 30 MB. Waiting for Wi-Fi if you're not on it." else "About 30 MB, over any network.", fontSize = 13.sp, color = OneUi.colors.textSecondary)
                    if (m.wifiOnly) Text("Use mobile data instead", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent, modifier = Modifier.clickable { Translate.download(false) }.padding(vertical = 8.dp))
                }
                else -> Column(Modifier.padding(18.dp)) {
                    Text("On-device translation", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text("Translates each line to English with ML Kit. The model (about 30 MB) also downloads by itself the first time you open a line.", fontSize = 13.sp, lineHeight = 18.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                    if (m is ModelState.Failed) Text(m.message, fontSize = 13.sp, color = OneUi.colors.error, modifier = Modifier.padding(bottom = 8.dp))
                    PillButton("Download model (~30 MB)", { Translate.download(JpPrefs.wifiOnly) }, modifier = Modifier.fillMaxWidth())
                }
            }
            CardDivider()
            ListRow(
                "Download over Wi-Fi only", summary = "Applies to the translation model",
                trailing = { OneUiSwitch(JpPrefs.wifiOnly) }, onClick = { JpPrefs.changeWifiOnly(!JpPrefs.wifiOnly) },
            )
        }

        sectionLabel("Display")
        card("display") {
            ListRow(
                "Romaji", summary = "Show Latin letters under the kana",
                trailing = { OneUiSwitch(JpPrefs.romaji) }, onClick = { JpPrefs.changeRomaji(!JpPrefs.romaji) },
            )
        }
        description("Text is recognised on this phone. Horizontal text works best; vertical manga text is supported but less reliable.")

        sectionLabel("My words (${words.size})", trailing = {
            if (words.isNotEmpty()) Text("Practice", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = OneUi.colors.accent, modifier = Modifier.clip(CircleShape).clickable { practicing = true }.padding(horizontal = 12.dp, vertical = 6.dp))
        })
        if (words.isEmpty()) {
            card("empty") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No words yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text("Tap the star on a word while reading and it will be saved here for practice.", fontSize = 13.5.sp, lineHeight = 19.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            item(key = "practiceBtn") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
                    PillButton("Practice ${words.size} word${if (words.size == 1) "" else "s"}", { practicing = true }, modifier = Modifier.fillMaxWidth())
                }
            }
            cardList(words, key = { "word-" + it.id }) { w -> SavedWordRow(w) }
        }
        description("Dictionary: JMdict (EDRDG), CC BY-SA 4.0. Word splitting: Kuromoji. Text recognition and translation: ML Kit.")
    }
}

@Composable
private fun SavedWordRow(w: SavedWord) {
    val c = OneUi.colors
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RubyWord(w.segments, 26.sp)
            Text(w.meaning.ifEmpty { "No meaning saved" }, fontSize = 14.sp, lineHeight = 19.sp, color = c.text, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
            Text(
                listOf(w.pos, "added " + DateFmt.format(Instant.ofEpochMilli(w.added).atZone(ZoneId.systemDefault()))).filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable { WordStore.remove(w.key) }, contentAlignment = Alignment.Center) {
            Icon(OneUiIcons.Close, "Delete", tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Simple flashcards: word first, tap to reveal reading + meaning, then Again / Got it. */
@Composable
private fun Flashcards(onClose: () -> Unit) {
    val c = OneUi.colors
    val saved by WordStore.words.collectAsState()
    // A snapshot of the deck for this session: least-known first, oldest first.
    val deck = remember { mutableStateOf(saved.sortedWith(compareBy({ it.box }, { it.added }))) }
    var queue by remember { mutableStateOf(deck.value) }
    var revealed by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var gotIt by remember { mutableIntStateOf(0) }
    val missed = remember { mutableSetOf<String>() }
    val total = deck.value.size
    val card = queue.firstOrNull()

    ToolPage("Practice", onClose) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (card == null) {
                Spacer(Modifier.weight(1f))
                Text("All done", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.text)
                Text("$gotIt of $total words knew on the first go. Come back tomorrow and read something new.", fontSize = 15.sp, lineHeight = 21.sp, color = c.textSecondary, modifier = Modifier.padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PillButton("Back to My words", onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
                Spacer(Modifier.weight(1f))
            } else {
                Text("${(total - queue.size + 1).coerceAtMost(total)} / $total  ·  ${queue.size} to go", fontSize = 13.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).clickable { revealed = true }.padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Before the reveal there is no furigana: you try to read it first.
                        RubyWord(card.segments, 52.sp, showRuby = revealed)
                        if (revealed) {
                            Text(card.reading.ifEmpty { card.word }, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.padding(top = 18.dp))
                            if (JpPrefs.romaji) Text(Kana.toRomaji(card.reading), fontSize = 15.sp, color = c.textSecondary)
                            Text(card.meaning.ifEmpty { "No meaning saved" }, fontSize = 18.sp, lineHeight = 25.sp, color = c.text, modifier = Modifier.padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (card.pos.isNotEmpty()) Text(card.pos, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text("Tap to reveal", fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(top = 28.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Again", {
                        WordStore.setBox(card.key, 0); missed.add(card.key)
                        queue = queue.drop(1) + card; revealed = false; done++
                    }, modifier = Modifier.weight(1f), tonal = true, enabled = revealed)
                    PillButton("Got it", {
                        WordStore.setBox(card.key, card.box + 1)
                        if (card.key !in missed) gotIt++
                        queue = queue.drop(1); revealed = false; done++
                    }, modifier = Modifier.weight(1f), enabled = revealed)
                }
            }
        }
    }
}
