package com.tools.app.audio

import com.tools.app.ui.OneUiIcons
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tools.app.ui.CardMargin
import com.tools.app.ui.ListRow
import com.tools.app.ui.OneUiScaffold
import com.tools.app.ui.PillButton
import com.tools.app.ui.RoundCheck
import com.tools.app.ui.cardList
import com.tools.app.ui.description
import com.tools.app.ui.formatDuration
import com.tools.app.ui.theme.OneUi
import com.tools.app.ui.theme.SamsungFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(val uri: Uri, val title: String, val subtitle: String, val durationMs: Long, val folder: String)

private val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private suspend fun loadSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val cols = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.RELATIVE_PATH,
    )
    val list = mutableListOf<Song>()
    context.contentResolver.query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.DURATION} > 0", null,
        "${MediaStore.Audio.Media.DATE_ADDED} DESC",
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val folder = c.getString(4).orEmpty().trimEnd('/').substringAfterLast('/')
            val artist = c.getString(2)?.takeUnless { it == "<unknown>" }.orEmpty()
            list += Song(
                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                title = c.getString(1).orEmpty(),
                subtitle = listOf(artist, folder).filter { it.isNotBlank() }.joinToString(" · "),
                durationMs = c.getLong(3),
                folder = folder,
            )
        }
    }
    list
}

/**
 * One UI song picker over the phone's music library (newest first, searchable). Falls back to the
 * system file picker for files outside the library or if access to music is declined.
 */
@Composable
fun AudioPicker(multiple: Boolean, onPicked: (List<Uri>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = OneUi.colors
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val chosen = remember { mutableStateListOf<Uri>() }

    val files = rememberLauncherForActivityResult(com.tools.app.ui.FilesPickMultiple()) { uris ->
        if (uris.isNotEmpty()) onPicked(if (multiple) uris else uris.take(1))
    }
    val browse = { files.launch(arrayOf("audio/*")) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) { browse() }
    }
    LaunchedEffect(granted) { if (granted) songs = loadSongs(context) else ask.launch(permission) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = { if (searching) { query = ""; searching = false; focusManager.clearFocus() } else onDismiss() })
        val q = query.trim()
        val shown = songs.orEmpty().filter { q.isEmpty() || it.title.contains(q, true) || it.subtitle.contains(q, true) }
        OneUiScaffold(
            title = if (multiple) "Choose songs" else "Choose a song",
            onBack = onDismiss,
            expandedTitle = false,
            actions = {
                Text(
                    "Files", fontSize = 16.sp, color = c.text,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = browse).padding(horizontal = 12.dp, vertical = 8.dp),
                )
                // The chosen songs are added from the toolbar, so the bottom stays My Files' search pill.
                if (multiple && chosen.isNotEmpty()) Text(
                    "Add ${chosen.size}", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = c.accent,
                    modifier = Modifier.clip(CircleShape).clickable { onPicked(chosen.toList()) }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            bottomBar = {
                com.tools.app.ui.PageSearch(searching, query, { query = it }, onOpen = { searching = true },
                    onClose = { query = ""; searching = false; focusManager.clearFocus() })
            },
        ) {
            when {
                !granted -> description("Allow access to music to list your songs here, or tap Files to browse.", key = "perm")
                songs == null -> description("Loading songs…", key = "loading")
                shown.isEmpty() -> description(if (q.isEmpty()) "No songs found. Tap Files to browse." else "No songs match \"$q\".", key = "empty")
            }
            cardList(shown, key = { it.uri.toString() }) { s ->
                val sel = s.uri in chosen
                ListRow(
                    s.title,
                    summary = listOf(s.subtitle, formatDuration((s.durationMs / 1000).toInt())).filter { it.isNotBlank() }.joinToString(" · "),
                    trailing = if (multiple) ({ RoundCheck(sel) }) else null,
                    onClick = {
                        if (!multiple) onPicked(listOf(s.uri))
                        else if (sel) chosen.remove(s.uri) else chosen.add(s.uri)
                    },
                )
            }
        }
    }
}
