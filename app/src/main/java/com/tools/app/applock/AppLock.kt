package com.tools.app.applock

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Relock(val label: String, val millis: Long) {
    Immediately("Immediately after leaving", 0),
    OneMinute("1 minute after leaving", 60_000),
    FiveMinutes("5 minutes after leaving", 300_000),
    ScreenOff("Only when the screen turns off", Long.MAX_VALUE),
}

data class AppLockConfig(
    val enabled: Boolean = true,
    val locked: Set<String> = emptySet(),
    val relock: Relock = Relock.Immediately,
)

/** Settings + unlock session state. Everything stays in app-private storage; nothing leaves the device. */
object AppLock {
    private lateinit var appContext: Context
    private val prefs by lazy { appContext.getSharedPreferences("applock", Context.MODE_PRIVATE) }
    /** Plain file instead of SharedPreferences: those are cached per process and never see the other process's writes. */
    private val file by lazy { android.util.AtomicFile(java.io.File(appContext.filesDir, "applock.conf")) }
    private val _config = MutableStateFlow(AppLockConfig())
    val config: StateFlow<AppLockConfig> = _config.asStateFlow()

    /** Package names unlocked in this session -> time the user left them (0 = still in it). */
    private val sessions = mutableMapOf<String, Long>()

    const val ACTION_RELOAD = "com.tools.app.applock.RELOAD"

    fun init(context: Context) {
        appContext = context.applicationContext
        reload()
    }

    /** Re-reads settings (the lock runs in its own ":lock" process; the UI pings it after changes). */
    fun reload() {
        val lines = runCatching { String(file.readFully()).lines() }.getOrNull()
        _config.value = if (lines == null) AppLockConfig( // first run: migrate old preferences
            enabled = prefs.getBoolean("enabled", true),
            locked = prefs.getStringSet("locked", emptySet())!!.toSet(),
            relock = runCatching { Relock.valueOf(prefs.getString("relock", null)!!) }.getOrDefault(Relock.Immediately),
        ) else AppLockConfig(
            enabled = lines.getOrNull(0) != "off",
            relock = runCatching { Relock.valueOf(lines[1]) }.getOrDefault(Relock.Immediately),
            locked = lines.drop(2).filter { it.isNotBlank() }.toSet(),
        )
    }

    private fun save(c: AppLockConfig) {
        _config.value = c
        val out = file.startWrite()
        try {
            out.write((listOf(if (c.enabled) "on" else "off", c.relock.name) + c.locked).joinToString("\n").toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) { file.failWrite(out) }
        appContext.sendBroadcast(android.content.Intent(ACTION_RELOAD).setPackage(appContext.packageName))
    }

    fun setEnabled(on: Boolean) = save(_config.value.copy(enabled = on))
    fun setRelock(r: Relock) = save(_config.value.copy(relock = r))
    fun setLocked(pkg: String, locked: Boolean) = save(
        _config.value.let { it.copy(locked = if (locked) it.locked + pkg else it.locked - pkg) }
    )

    @Synchronized fun markUnlocked(pkg: String) { sessions[pkg] = 0L }

    /** App whose lock screen is currently up (its own later windows shouldn't lock it again). */
    @Volatile var gating: String? = null
        set(value) { field = value; gatingSince = SystemClock.elapsedRealtime() }
    @Volatile private var gatingSince = 0L
    /** True while the lock screen is the resumed, top activity (set by LockActivity, same process). */
    @Volatile var lockOnTop = false

    /** The first moments after a lock opens, while the launcher and app are still animating. */
    fun gateStarting() = SystemClock.elapsedRealtime() - gatingSince < 1200

    /** Apps the user just backed out of, so one popping itself up again goes straight Home. */
    private val cancelledAt = mutableMapOf<String, Long>()
    @Synchronized fun onCancelled(pkg: String) { cancelledAt[pkg] = SystemClock.elapsedRealtime() }
    @Synchronized fun recentlyCancelled(pkg: String): Boolean =
        cancelledAt[pkg]?.let { SystemClock.elapsedRealtime() - it < 2000 } ?: false

    /** Called when [pkg] comes to the foreground. Returns true if the lock screen must be shown. */
    @Synchronized fun needsLock(pkg: String): Boolean {
        val c = _config.value
        if (!c.enabled || pkg !in c.locked) return false
        val leftAt = sessions[pkg] ?: return true
        if (leftAt == 0L) return false
        val expired = c.relock != Relock.ScreenOff && SystemClock.elapsedRealtime() - leftAt >= c.relock.millis
        if (expired) sessions.remove(pkg) else sessions[pkg] = 0L
        return expired
    }

    /** The user moved to [current]; start relock timers for every other unlocked app. */
    @Synchronized fun onForeground(current: String) {
        val now = SystemClock.elapsedRealtime()
        for ((pkg, leftAt) in sessions) if (pkg != current && leftAt == 0L) sessions[pkg] = now
        if (_config.value.relock == Relock.Immediately) sessions.keys.retainAll { it == current }
    }

    @Synchronized fun onScreenOff() { sessions.clear(); gating = null }

    /** True only when Android has actually activated the service (a "restricted setting" block leaves the toggle on but inactive). */
    fun isServiceEnabled(context: Context): Boolean {
        val me = ComponentName(context, AppLockService::class.java)
        val am = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == me }
    }

    fun isToggleOnInSettings(context: Context): Boolean {
        val me = ComponentName(context, AppLockService::class.java)
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
