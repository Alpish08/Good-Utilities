package com.tools.app.music

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tools.app.music.MusicDownloader
import com.tools.app.music.engine.EngineState
import com.tools.app.music.engine.Options
import com.tools.app.music.engine.Quality
import com.tools.app.ui.*
import com.tools.app.ui.theme.OneUi

private data class Toggle(val title: String, val summary: String, val get: (Options) -> Boolean, val set: (Options, Boolean) -> Options)

private val TOGGLES = listOf(
    Toggle("Square album covers", "Crop YouTube's 16:9 thumbnail to a square", { it.squareCovers }, { o, v -> o.copy(squareCovers = v) }),
    Toggle("Number tracks", "Prefix file names with the playlist position", { it.numberTracks }, { o, v -> o.copy(numberTracks = v) }),
    Toggle("Folder per playlist", "Good Utilities/Music/<playlist name>", { it.subfolder }, { o, v -> o.copy(subfolder = v) }),
    Toggle("Skip existing songs", "Re-download only what's new when a playlist grows", { it.skipExisting }, { o, v -> o.copy(skipExisting = v) }),
)

@Composable
fun MusicSettingsScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var signedIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.tools.app.music.engine.YouTubeAccount.isSignedIn(context)) }
    val options by MusicDownloader.settings.flow.collectAsState()
    val s by MusicDownloader.state.collectAsState()

    OneUiScaffold(
        title = "Download Settings",
        onBack = onBack,
    ) {
        sectionLabel("MP3 quality")
        cardList(Quality.entries, key = { it.name }) { q ->
            ListRow(
                q.label, summary = q.summary,
                onClick = { MusicDownloader.settings.update { it.copy(quality = q) } },
                leading = { RadioDot(options.quality == q) },
            )
        }

        sectionLabel("Files")
        cardList(TOGGLES, key = { it.title }) { t ->
            val on = t.get(options)
            ListRow(
                t.title, summary = t.summary,
                onClick = { MusicDownloader.settings.update { t.set(it, !on) } },
                trailing = { OneUiSwitch(on) },
            )
        }

        sectionLabel("YouTube account")
        card("account") {
            if (signedIn) {
                ListRow(
                    "Signed in",
                    summary = "Downloads use your account (Premium audio when YouTube offers it). Falls back to signed-out automatically.",
                    summaryColor = OneUi.colors.accent,
                )
                com.tools.app.ui.CardDivider()
                ListRow("Sign out", onClick = { com.tools.app.music.engine.YouTubeAccount.signOut(context); signedIn = false })
            } else {
                ListRow(
                    "Sign in with YouTube Premium",
                    summary = "Optional. Uses Google's own sign-in page; Good Utilities never sees your password. Can reduce download blocks.",
                    onClick = onSignIn,
                )
            }
        }

        sectionLabel("Download engine")
        card("engine") {
            val engine = s.engine
            ListRow(
                "yt-dlp",
                summary = when (engine) {
                    is EngineState.Ready -> engine.version ?: "Bundled version"
                    EngineState.Updating -> "Updating…"
                    EngineState.Preparing -> "Preparing…"
                    is EngineState.Failed -> engine.message
                },
            )
            com.tools.app.ui.CardDivider()
            ListRow(
                "Update yt-dlp",
                summary = s.engineMessage ?: "YouTube changes often. Update if downloads start failing.",
                summaryColor = if (s.engineMessage != null) OneUi.colors.accent else null,
                enabled = engine is EngineState.Ready,
                onClick = MusicDownloader::updateEngine,
                trailing = if (engine == EngineState.Updating) {
                    { CircularProgressIndicator(Modifier.size(24.dp), color = OneUi.colors.accent, strokeWidth = 2.5.dp) }
                } else null,
            )
        }

        sectionLabel("About")
        card("about") {
            ListRow("Save location", summary = "Internal storage › Good Utilities › Music")
            com.tools.app.ui.CardDivider()
            ListRow("For personal use", summary = "Only download content you have the rights to keep offline.")
        }
    }
}
