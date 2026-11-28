package com.tools.app.music

import com.tools.app.R
import com.tools.app.ui.t
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

private val TOGGLES get() = listOf(
    Toggle(t(R.string.music_square_album_covers), t(R.string.music_crop_youtube_s_16_9_thumbnail_to_a_squ), { it.squareCovers }, { o, v -> o.copy(squareCovers = v) }),
    Toggle(t(R.string.music_number_tracks), t(R.string.music_prefix_file_names_with_the_playlist_po), { it.numberTracks }, { o, v -> o.copy(numberTracks = v) }),
    Toggle(t(R.string.music_folder_per_playlist), t(R.string.music_good_utilities_music_playlist_name), { it.subfolder }, { o, v -> o.copy(subfolder = v) }),
    Toggle(t(R.string.music_skip_existing_songs), t(R.string.music_re_download_only_what_s_new_when_a_pla), { it.skipExisting }, { o, v -> o.copy(skipExisting = v) }),
)

@Composable
fun MusicSettingsScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var signedIn by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.tools.app.music.engine.YouTubeAccount.isSignedIn(context)) }
    val options by MusicDownloader.settings.flow.collectAsState()
    val s by MusicDownloader.state.collectAsState()

    OneUiScaffold(
        title = t(R.string.music_download_settings),
        onBack = onBack,
    ) {
        sectionLabel(t(R.string.music_mp3_quality))
        cardList(Quality.entries, key = { it.name }) { q ->
            ListRow(
                q.label, summary = q.summary,
                onClick = { MusicDownloader.settings.update { it.copy(quality = q) } },
                leading = { RadioDot(options.quality == q) },
            )
        }

        sectionLabel(t(R.string.home_files))
        cardList(TOGGLES, key = { it.title }) { t ->
            val on = t.get(options)
            ListRow(
                t.title, summary = t.summary,
                onClick = { MusicDownloader.settings.update { t.set(it, !on) } },
                trailing = { OneUiSwitch(on) },
            )
        }

        sectionLabel(t(R.string.music_youtube_account))
        card("account") {
            if (signedIn) {
                ListRow(
                    t(R.string.music_signed_in),
                    summary = t(R.string.music_downloads_use_your_account_premium_aud),
                    summaryColor = OneUi.colors.accent,
                )
                com.tools.app.ui.CardDivider()
                ListRow(t(R.string.music_sign_out), onClick = { com.tools.app.music.engine.YouTubeAccount.signOut(context); signedIn = false })
            } else {
                ListRow(
                    t(R.string.music_sign_in_with_youtube_premium),
                    summary = t(R.string.music_optional_uses_google_s_own_sign_in_pag),
                    onClick = onSignIn,
                )
            }
        }

        sectionLabel(t(R.string.music_download_engine))
        card("engine") {
            val engine = s.engine
            ListRow(
                "yt-dlp",
                summary = when (engine) {
                    is EngineState.Ready -> engine.version ?: t(R.string.music_bundled_version)
                    EngineState.Updating -> t(R.string.music_updating)
                    EngineState.Preparing -> t(R.string.music_preparing)
                    is EngineState.Failed -> engine.message
                },
            )
            com.tools.app.ui.CardDivider()
            ListRow(
                t(R.string.music_update_yt_dlp),
                summary = s.engineMessage ?: t(R.string.music_youtube_changes_often_update_if_downlo),
                summaryColor = if (s.engineMessage != null) OneUi.colors.accent else null,
                enabled = engine is EngineState.Ready,
                onClick = MusicDownloader::updateEngine,
                trailing = if (engine == EngineState.Updating) {
                    { com.tools.app.ui.OneUiSpinner(Modifier.size(24.dp), color = OneUi.colors.accent, strokeWidth = 2.5.dp) }
                } else null,
            )
        }

        sectionLabel(t(R.string.home_about))
        card("about") {
            ListRow(t(R.string.music_save_location), summary = t(R.string.music_internal_storage_good_utilities_music))
            com.tools.app.ui.CardDivider()
            ListRow(t(R.string.music_for_personal_use), summary = t(R.string.music_only_download_content_you_have_the_rig))
        }
    }
}
