package com.tools.app.dns

import com.tools.app.R
import com.tools.app.ui.t
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import com.tools.app.battery.ChargeLimit

/** Private DNS switch: "Custom" (hostname) when on, "Automatic" when off. Needs WRITE_SECURE_SETTINGS. */
object PrivateDns {
    private const val KEY_MODE = "private_dns_mode"
    private const val KEY_HOST = "private_dns_specifier"
    const val MODE_CUSTOM = "hostname"
    const val MODE_AUTO = "opportunistic"

    val PRESETS get() = listOf(
        "dns.adguard.com" to t(R.string.dns_adguard_blocks_ads_and_trackers),
        "dns.adguard-dns.com" to t(R.string.dns_adguard_new_address),
        "one.one.one.one" to t(R.string.dns_cloudflare_fast_no_filtering),
        "security.cloudflare-dns.com" to t(R.string.dns_cloudflare_blocks_malware),
        "dns.quad9.net" to t(R.string.dns_quad9_blocks_malware),
        "base.dns.mullvad.net" to t(R.string.dns_mullvad_no_logging),
    )

    fun canWrite(context: Context) = ChargeLimit.canWrite(context)
    fun mode(context: Context): String = Settings.Global.getString(context.contentResolver, KEY_MODE) ?: MODE_AUTO
    fun host(context: Context): String = Settings.Global.getString(context.contentResolver, KEY_HOST).orEmpty()
    fun isCustom(context: Context) = mode(context) == MODE_CUSTOM && host(context).isNotBlank()

    fun setCustom(context: Context, on: Boolean) {
        Settings.Global.putString(context.contentResolver, KEY_MODE, if (on) MODE_CUSTOM else MODE_AUTO)
        refreshTile(context)
    }

    fun setHost(context: Context, host: String) {
        Settings.Global.putString(context.contentResolver, KEY_HOST, host.trim())
        refreshTile(context)
    }

    /** Calls [onChange] on the main thread whenever the Private DNS mode or host changes. */
    fun observe(context: Context, onChange: () -> Unit): android.database.ContentObserver {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        for (key in listOf(KEY_MODE, KEY_HOST)) {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        return observer
    }

    fun refreshTile(context: Context) =
        TileService.requestListeningState(context, ComponentName(context, PrivateDnsTileService::class.java))
}
