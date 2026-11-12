package com.tools.app.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives Samsung's battery figures from sync-battery.sh (adb shell am broadcast …). The manifest requires the
 * sender to hold android.permission.DUMP, which adb's shell has and regular apps don't, so other apps can't
 * plant fake values.
 */
class SamsungBatterySync : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val asoc = intent.getIntExtra("asoc", -1).takeIf { it in 1..100 } ?: return
        BatteryHealth.saveSamsung(
            context, asoc,
            usage = intent.getIntExtra("usage", -1).takeIf { it >= 0 },
            firstUse = intent.getStringExtra("first"),
            made = intent.getStringExtra("made"),
        )
        resultData = "saved"
    }

    companion object {
        const val ACTION = "com.tools.app.SAMSUNG_BATTERY"
    }
}
