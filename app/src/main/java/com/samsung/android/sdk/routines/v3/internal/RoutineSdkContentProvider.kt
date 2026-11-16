package com.samsung.android.sdk.routines.v3.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.tools.app.battery.BypassActionActivity
import com.tools.app.battery.BypassTileService
import com.tools.app.battery.ChargeLimit
import org.json.JSONObject

/**
 * Modes and Routines actions for Good Utilities, run in the background like Samsung's own plugins: no screen opens,
 * so the app you're in stays in front (an "open activity" action sends you to the Home screen).
 *
 * Speaks the protocol of Samsung's Routines SDK v3: Routines calls [call] with a method name and extras
 * ("type" = "action", "tag", "instanceId", "parameterValues" as JSON). The class name must stay exactly this, because
 * Routines looks the provider up by it. Only Routines can call it (see the manifest's permission).
 */
class RoutineSdkContentProvider : ContentProvider() {

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (extras?.getString("type") != "action") return null
        val tag = extras.getString("tag") ?: return null
        if (tag != TAG_BYPASS) return null
        val token = Binder.clearCallingIdentity()
        try {
            val on = param(extras.getString("parameterValues"), TAG_BYPASS)
            return when (method) {
                "isSupport" -> result(SUPPORTED)
                "isValid" -> result(if (ChargeLimit.canWrite(ctx)) VALID else NOT_AVAILABLE)
                "getLabelParam" -> Bundle().apply { putString("labelParams", if (on == false) "Off" else "On") }
                "getCurrentParam" -> Bundle().apply {
                    putString("parameterValues", values(TAG_BYPASS, ChargeLimit.activeLimit(ctx) != null))
                }
                "performAction" -> {
                    val ok = ChargeLimit.canWrite(ctx)
                    if (ok) {
                        if (on != false) {
                            val level = BypassTileService.currentLevel(ctx).coerceIn(ChargeLimit.MIN_LIMIT, 100)
                            ChargeLimit.apply(ctx, level)
                            main.post { BypassActionActivity.toastOn(ctx, level) }
                        } else {
                            ChargeLimit.restore(ctx)
                            main.post { BypassActionActivity.toastOff(ctx) }
                        }
                    }
                    result(if (ok) SUCCESS else FAIL_NOT_AVAILABLE).apply {
                        putLong("instanceId", extras.getLong("instanceId", -1))
                    }
                }
                else -> null // recoverAction and the template / error-dialog requests: nothing to do
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun result(code: Int) = Bundle().apply { putInt("resultInt", code) }

    /** parameterValues: {"key": "{\"TYPE\":\"BOOLEAN\",\"VALUE\":true}"} */
    private fun param(json: String?, key: String): Boolean? = runCatching {
        val inner = JSONObject(JSONObject(json ?: return null).getString(key))
        inner.getBoolean("VALUE")
    }.getOrNull()

    private fun values(key: String, value: Boolean): String =
        JSONObject().put(key, JSONObject().put("TYPE", "BOOLEAN").put("VALUE", value).toString()).toString()

    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = -1
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = -1

    private companion object {
        const val TAG_BYPASS = "bypass_charging"
        const val SUCCESS = 1
        const val FAIL_NOT_AVAILABLE = -2
        const val SUPPORTED = 1
        const val VALID = 1
        const val NOT_AVAILABLE = -2
        val main = Handler(Looper.getMainLooper())
    }
}
