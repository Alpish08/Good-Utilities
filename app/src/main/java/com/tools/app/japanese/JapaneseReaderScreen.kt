package com.tools.app.japanese

import com.tools.app.ui.tp
import com.tools.app.ui.t
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

    val picker = rememberLauncherForActivityResult(com.tools.app.ui.GalleryPick()) { uri: Uri? ->
        if (uri != null) JapaneseReaderActivity.open(ctx, uri)
    }
    val photoFile = remember { File(ctx.cacheDir, "japanese/photo.jpg").also { it.parentFile?.mkdirs() } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok && photoFile.length() > 0) JapaneseReaderActivity.openFile(ctx, photoFile)
    }

    OneUiScaffold(title = t(R.string.home_japanese_reader), onBack = onBack, actions = { com.tools.app.ui.TipsMenu(listOf(
        com.tools.app.ui.Tip(t(R.string.jp_share_an_image), t(R.string.jp_share_an_image_from_any_app_and_pick_r)),
        com.tools.app.ui.Tip(t(R.string.jp_recognition), t(R.string.jp_text_is_recognised_on_this_phone_horiz)),
        com.tools.app.ui.Tip(t(R.string.jp_credits), t(R.string.jp_dictionary_jmdict_edrdg_cc_by_sa_4_0_w)),
    )) }) {
        if (notice != null) card("notice") {
            Text(notice, fontSize = 14.5.sp, lineHeight = 21.sp, color = OneUi.colors.text, modifier = Modifier.padding(18.dp))
        }

        sectionLabel(t(R.string.jp_read_something))
        card("capture") {
            ListRow(
                t(R.string.color_choose_image), summary = t(R.string.jp_a_screenshot_or_picture_with_japanese),
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            CardDivider()
            ListRow(
                t(R.string.jp_take_photo), summary = t(R.string.jp_a_sign_a_page_a_game_screen),
                onClick = {
                    photoFile.delete()
                    camera.launch(FileProvider.getUriForFile(ctx, "com.tools.app.files", photoFile))
                },
            )
        }

        sectionLabel(t(R.string.jp_quick_settings_tile))
        card("tile") {
            ListRow(
                t(R.string.jp_read_the_screen_you_are_on),
                summary = t(R.string.jp_swipe_down_tap_read_japanese_works_ove),
            )
            CardDivider()
            ListRow(
                if (serviceOn) t(R.string.jp_screen_capture_is_ready) else t(R.string.jp_app_lock_s_accessibility_service_is_of),
                summary = if (serviceOn) t(R.string.jp_uses_the_same_accessibility_service_as)
                else t(R.string.jp_the_tile_needs_it_to_take_the_screensh),
                summaryColor = if (serviceOn) null else OneUi.colors.error,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
                    PillButton(t(R.string.jp_add_tile_to_quick_settings), {
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

        sectionLabel(t(R.string.jp_dictionary))
        card("dict") {
            val s = dictState
            when {
                s is DictState.Working -> Column(Modifier.padding(18.dp)) {
                    Text(t(R.string.jp_preparing_the_dictionary), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Spacer(Modifier.height(10.dp))
                    ThinProgress(s.progress)
                    Spacer(Modifier.height(8.dp))
                    Text(t(R.string.jp_downloading_jmdict_and_indexing_it_for, (s.progress * 100).toInt()), fontSize = 13.sp, color = OneUi.colors.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(t(R.string.ui_cancel), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent, modifier = Modifier.clickable { DictJobs.cancel() }.padding(vertical = 4.dp))
                }
                s is DictState.Checking -> ListRow(t(R.string.jp_checking_the_latest_jmdict_release))
                stats != null -> {
                    ListRow(
                        t(R.string.jp_english_dictionary_installed), summary = t(R.string.jp_entries_on_this_phone_njmdict_edrdg_cc, "%,d".format(stats.first), formatMb(stats.second)),
                        trailing = { Icon(OneUiIcons.CheckCircle, null, tint = OneUi.colors.success, modifier = Modifier.size(24.dp)) },
                    )
                    CardDivider()
                    ListRow(t(R.string.jp_remove_dictionary), summary = t(R.string.jp_frees_the_space_you_can_download_it_ag), onClick = { DictJobs.delete(ctx) })
                }
                else -> Column(Modifier.padding(18.dp)) {
                    Text(t(R.string.jp_offline_dictionary), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text(t(R.string.jp_meanings_and_parts_of_speech_for_every), fontSize = 13.sp, lineHeight = 18.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                    SegmentedControl(
                        listOf(t(R.string.jp_everything), t(R.string.jp_common_only)), if (JpPrefs.variant == DictVariant.Full) 0 else 1,
                        { JpPrefs.changeVariant(if (it == 0) DictVariant.Full else DictVariant.Common) },
                    )
                    Spacer(Modifier.height(12.dp))
                    if (s is DictState.Failed) Text(s.message, fontSize = 13.sp, color = OneUi.colors.error, modifier = Modifier.padding(bottom = 8.dp))
                    val size = assets[JpPrefs.variant]?.size ?: if (JpPrefs.variant == DictVariant.Full) 11_500_000L else 1_450_000L
                    PillButton(t(R.string.jp_download_dictionary, formatMb(size)), { DictJobs.start(ctx, JpPrefs.variant) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        sectionLabel(t(R.string.jp_translation))
        card("translate") {
            when (val m = model) {
                is ModelState.Ready -> {
                    ListRow(
                        t(R.string.jp_japanese_to_english_model_ready), summary = t(R.string.jp_sentences_are_translated_on_this_phone),
                        trailing = { Icon(OneUiIcons.CheckCircle, null, tint = OneUi.colors.success, modifier = Modifier.size(24.dp)) },
                    )
                    CardDivider()
                    ListRow(t(R.string.jp_remove_model), summary = t(R.string.jp_about_30_mb), onClick = { Translate.delete() })
                }
                is ModelState.Downloading -> Column(Modifier.padding(18.dp)) {
                    Text(t(R.string.jp_downloading_the_translation_model), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Spacer(Modifier.height(10.dp))
                    com.tools.app.ui.ThinProgressIndeterminate(color = OneUi.colors.accent)
                    Spacer(Modifier.height(6.dp))
                    Text(if (m.wifiOnly) t(R.string.jp_about_30_mb_waiting_for_wi_fi_if_you_r) else t(R.string.jp_about_30_mb_over_any_network), fontSize = 13.sp, color = OneUi.colors.textSecondary)
                    if (m.wifiOnly) Text(t(R.string.jp_use_mobile_data_instead), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.accent, modifier = Modifier.clickable { Translate.download(false) }.padding(vertical = 8.dp))
                }
                else -> Column(Modifier.padding(18.dp)) {
                    Text(t(R.string.jp_on_device_translation), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text(t(R.string.jp_translates_each_line_to_english_with_m), fontSize = 13.sp, lineHeight = 18.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 3.dp, bottom = 12.dp))
                    if (m is ModelState.Failed) Text(m.message, fontSize = 13.sp, color = OneUi.colors.error, modifier = Modifier.padding(bottom = 8.dp))
                    PillButton(t(R.string.jp_download_model_30_mb), { Translate.download(JpPrefs.wifiOnly) }, modifier = Modifier.fillMaxWidth())
                }
            }
            CardDivider()
            ListRow(
                t(R.string.jp_download_over_wi_fi_only), summary = t(R.string.jp_applies_to_the_translation_model),
                trailing = { OneUiSwitch(JpPrefs.wifiOnly) }, onClick = { JpPrefs.changeWifiOnly(!JpPrefs.wifiOnly) },
            )
        }

        sectionLabel(t(R.string.jp_display))
        card("display") {
            ListRow(
                t(R.string.jp_romaji), summary = t(R.string.jp_show_latin_letters_under_the_kana),
                trailing = { OneUiSwitch(JpPrefs.romaji) }, onClick = { JpPrefs.changeRomaji(!JpPrefs.romaji) },
            )
        }

        sectionLabel(t(R.string.jp_my_words, words.size), trailing = {
            if (words.isNotEmpty()) Text(t(R.string.jp_practice), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = OneUi.colors.accent, modifier = Modifier.clip(CircleShape).clickable { practicing = true }.padding(horizontal = 12.dp, vertical = 6.dp))
        })
        if (words.isEmpty()) {
            card("empty") {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t(R.string.jp_no_words_yet), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = OneUi.colors.text)
                    Text(t(R.string.jp_tap_the_star_on_a_word_while_reading_a), fontSize = 13.5.sp, lineHeight = 19.sp, color = OneUi.colors.textSecondary, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            item(key = "practiceBtn") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
                    PillButton(tp(R.plurals.jp_practice_words, words.size), { practicing = true }, modifier = Modifier.fillMaxWidth())
                }
            }
            cardList(words, key = { "word-" + it.id }) { w -> SavedWordRow(w) }
        }
    }
}

@Composable
private fun SavedWordRow(w: SavedWord) {
    val c = OneUi.colors
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            RubyWord(w.segments, 26.sp)
            Text(w.meaning.ifEmpty { t(R.string.jp_no_meaning_saved) }, fontSize = 14.sp, lineHeight = 19.sp, color = c.text, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
            Text(
                listOf(w.pos, "added " + DateFmt.format(Instant.ofEpochMilli(w.added).atZone(ZoneId.systemDefault()))).filter { it.isNotEmpty() }.joinToString(" · "),
                fontSize = 12.sp, color = c.textSecondary, modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable { WordStore.remove(w.key) }, contentAlignment = Alignment.Center) {
            Icon(OneUiIcons.Close, t(R.string.pdf_delete), tint = c.textSecondary, modifier = Modifier.size(20.dp))
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

    ToolPage(t(R.string.jp_practice), onClose) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (card == null) {
                Spacer(Modifier.weight(1f))
                Text(t(R.string.convert_all_done), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.text)
                Text(t(R.string.jp_of_words_knew_on_the_first_go_come_bac, gotIt, total), fontSize = 15.sp, lineHeight = 21.sp, color = c.textSecondary, modifier = Modifier.padding(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PillButton(t(R.string.jp_back_to_my_words), onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
                Spacer(Modifier.weight(1f))
            } else {
                Text(t(R.string.jp_to_go, (total - queue.size + 1).coerceAtMost(total), total, queue.size), fontSize = 13.5.sp, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(OneUi.CardShape).background(c.card).clickable { revealed = true }.padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Before the reveal there is no furigana: you try to read it first.
                        RubyWord(card.segments, 52.sp, showRuby = revealed)
                        if (revealed) {
                            Text(card.reading.ifEmpty { card.word }, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = c.accent, modifier = Modifier.padding(top = 18.dp))
                            if (JpPrefs.romaji) Text(Kana.toRomaji(card.reading), fontSize = 15.sp, color = c.textSecondary)
                            Text(card.meaning.ifEmpty { t(R.string.jp_no_meaning_saved) }, fontSize = 18.sp, lineHeight = 25.sp, color = c.text, modifier = Modifier.padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (card.pos.isNotEmpty()) Text(card.pos, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            Text(t(R.string.jp_tap_to_reveal), fontSize = 15.sp, color = c.textSecondary, modifier = Modifier.padding(top = 28.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(t(R.string.jp_again), {
                        WordStore.setBox(card.key, 0); missed.add(card.key)
                        queue = queue.drop(1) + card; revealed = false; done++
                    }, modifier = Modifier.weight(1f), tonal = true, enabled = revealed)
                    PillButton(t(R.string.jp_got_it), {
                        WordStore.setBox(card.key, card.box + 1)
                        if (card.key !in missed) gotIt++
                        queue = queue.drop(1); revealed = false; done++
                    }, modifier = Modifier.weight(1f), enabled = revealed)
                }
            }
        }
    }
}
