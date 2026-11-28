package com.tools.app

import android.app.Application
import com.tools.app.applock.AppLock
import com.tools.app.music.MusicDownloader

class ToolsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.tools.app.ui.Strings.app = this
        CrashLog.install(this)
        AppLock.init(this)
        // The App lock runs in a separate ":lock" process; keep that one as small as possible.
        if (!getProcessName().endsWith(":lock")) MusicDownloader.init(this)
    }
}
