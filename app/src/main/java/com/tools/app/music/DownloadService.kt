package com.tools.app.music

import com.tools.app.ui.t
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tools.app.MainActivity
import com.tools.app.R
import com.tools.app.music.engine.AppState
import com.tools.app.music.engine.Phase
import com.tools.app.music.engine.Status
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the process alive while downloading and mirrors progress into a notification. */
class DownloadService : Service() {
    private val scope = MainScope()
    private var job: Job? = null
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    private data class Snapshot(val phase: Phase, val done: Int, val total: Int, val current: String?, val permille: Int)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            MusicDownloader.cancel()
            return START_NOT_STICKY
        }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, t(R.string.music_music_downloads), NotificationManager.IMPORTANCE_LOW)
        )
        ServiceCompat.startForeground(
            this, NOTIF_PROGRESS, build(snapshot(MusicDownloader.state.value)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (job == null) job = scope.launch {
            MusicDownloader.state.map(::snapshot).distinctUntilChanged().collect { snap ->
                if (snap.phase == Phase.Downloading) {
                    nm.notify(NOTIF_PROGRESS, build(snap))
                } else {
                    if (snap.phase == Phase.Finished) nm.notify(NOTIF_DONE, finished(MusicDownloader.state.value))
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        MusicDownloader.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun snapshot(s: AppState): Snapshot {
        val current = s.playlist?.tracks?.firstOrNull {
            val st = s.trackStates[it.id]?.status
            st == Status.Downloading || st == Status.Converting
        }?.title
        return Snapshot(s.phase, s.finishedCount, s.totalQueued, current, (s.overallProgress * 200).toInt() * 5)
    }

    private val openApp by lazy {
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun build(snap: Snapshot): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spool)
            .setContentTitle(snap.current ?: t(R.string.music_preparing))
            .setContentText(t(R.string.reader_of, minOf(snap.done + 1, snap.total), snap.total))
            .setProgress(1000, snap.permille, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, t(R.string.ui_cancel), cancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun finished(s: AppState): Notification {
        val saved = s.count(Status.Done)
        val failed = s.count(Status.Failed)
        val text = buildString {
            append(t(R.string.music_saved_2, saved))
            s.count(Status.Skipped).takeIf { it > 0 }?.let { append(t(R.string.music_already_there, it)) }
            if (failed > 0) append(t(R.string.music_failed_2, failed))
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spool)
            .setContentTitle(s.playlist?.title ?: t(R.string.music_download_finished))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_PROGRESS = 1
        private const val NOTIF_DONE = 2
        private const val ACTION_CANCEL = "com.tools.app.music.CANCEL"
    }
}
