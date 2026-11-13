package com.tools.app.audio

import com.tools.app.ui.theme.IconColors
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import com.tools.app.ui.OneUiIcons
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tools.app.ui.CardMargin
import com.tools.app.ui.EmptyState
import com.tools.app.ui.fadingEdges
import com.tools.app.ui.JobStatus
import com.tools.app.ui.OneUiSlider
import com.tools.app.ui.PillButton
import com.tools.app.ui.ToolPage
import com.tools.app.ui.ToolbarIcon
import com.tools.app.ui.formatDuration
import com.tools.app.ui.theme.OneUi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MergeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = OneUi.colors
    val clips = AudioJobs.clips
    var fade by AudioJobs.crossfade
    val work by AudioJobs.merge.collectAsState()
    var adding by remember { mutableStateOf(false) }

    var picking by remember { mutableStateOf(false) }
    fun importAll(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        AudioJobs.resetMerge()
        adding = true
        scope.launch {
            var failed = 0
            for (u in uris) {
                runCatching { Ffmpeg.import(context, u, "m${System.nanoTime()}") }
                    .onSuccess { clips += it }.onFailure { failed++ }
            }
            adding = false
            if (failed > 0) Toast.makeText(context, "Couldn't add $failed ${if (failed == 1) "file" else "files"}", Toast.LENGTH_SHORT).show()
        }
    }
    if (picking) AudioPicker(multiple = true, onPicked = { picking = false; importAll(it) }, onDismiss = { picking = false })
    val add = { picking = true }
    val running = work is Work.Running

    ToolPage("Audio merger", onBack, expandable = clips.isNotEmpty(), actions = { if (clips.isNotEmpty() && !running) ToolbarIcon(OneUiIcons.Add, "Add audio", add) }) {
        if (clips.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Rounded.QueueMusic, IconColors.Orange, "Merge songs",
                "Join tracks into one MP3, blending each into the next with a smooth crossfade.",
                if (adding) "Adding…" else "Add audio files", add,
            )
            return@ToolPage
        }

        val list = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().fadingEdges(list), state = list) {
            itemsIndexed(clips, key = { _, it -> it.file.name }) { i, clip ->
                val first = i == 0; val last = i == clips.lastIndex
                val r = OneUi.CardRadius
                Row(
                    Modifier
                        .padding(start = CardMargin, end = CardMargin, top = if (first) 6.dp else 0.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
                            bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
                        ))
                        .background(c.card)
                        .padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.accent, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(clip.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDuration((clip.durationMs / 1000).toInt()), fontSize = 13.sp, color = c.textSecondary)
                    }
                    if (!running) {
                        SmallIcon(OneUiIcons.ChevronUp, "Move up", !first) { clips.add(i - 1, clips.removeAt(i)) }
                        SmallIcon(OneUiIcons.ChevronDown, "Move down", !last) { clips.add(i + 1, clips.removeAt(i)) }
                        SmallIcon(OneUiIcons.Close, "Remove", true) { clips.removeAt(i); AudioJobs.resetMerge() }
                    }
                }
            }
            if (adding) item { Text("Adding…", fontSize = 14.sp, color = c.textSecondary, modifier = Modifier.padding(28.dp, 10.dp)) }
        }

        // Bottom card: settings and the action.
        Column(
            Modifier
                .padding(horizontal = CardMargin, vertical = 6.dp)
                .fillMaxWidth()
                .clip(OneUi.CardShape)
                .background(c.card)
                .padding(18.dp),
        ) {
            val effective = AudioJobs.effectiveFade()
            Row(Modifier.fillMaxWidth()) {
                Text("Crossfade", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                Text(if (effective <= 0f) "Off" else "${"%.1f".format(effective)} s", fontSize = 15.sp, color = c.textSecondary)
            }
            OneUiSlider(fade, { if (!running) { fade = (it * 2).roundToInt() / 2f; AudioJobs.resetMerge() } }, valueRange = 0f..12f)
            Text(
                "${clips.size} tracks · ${formatDuration((AudioJobs.mergedLengthMs() / 1000).toInt())} total · high-quality MP3",
                fontSize = 13.sp, color = c.textSecondary,
            )
            JobStatus(
                running = (work as? Work.Running)?.progress,
                done = (work as? Work.Done)?.fileName,
                error = (work as? Work.Failed)?.message,
                onCancel = AudioJobs::cancelMerge,
                modifier = Modifier.padding(top = 12.dp),
            )
            PillButton(
                if (running) "Merging…" else "Merge to MP3",
                enabled = !running && !adding && clips.size >= 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                onClick = { AudioJobs.startMerge(context) },
            )
        }
    }
}

@Composable
internal fun SmallIcon(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = OneUi.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) c.text else c.textSecondary.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
    }
}
