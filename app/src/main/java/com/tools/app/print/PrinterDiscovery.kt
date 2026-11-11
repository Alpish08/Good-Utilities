package com.tools.app.print

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.Executor

/** A printer found through mDNS (or typed in). [key] identifies the physical device. */
data class FoundPrinter(
    val key: String,
    val address: PrinterAddress,
    val name: String,
    val model: String,
    val note: String = "",
    val uuid: String? = null,
    val color: Boolean? = null,
    val duplex: Boolean? = null,
    val pdl: List<String> = emptyList(),
)

/** Bonjour discovery of `_ipp._tcp` / `_ipps._tcp`. Holds a multicast lock only while scanning. */
object PrinterDiscovery {
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }

    private val _printers = MutableStateFlow<List<FoundPrinter>>(emptyList())
    val printers: StateFlow<List<FoundPrinter>> = _printers
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private var nsd: NsdManager? = null
    private var lock: WifiManager.MulticastLock? = null
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val stopper = Runnable { stop() }

    // Pre-API-34 resolving: strictly one at a time.
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val services = mutableMapOf<String, FoundPrinter>() // serviceName|type -> printer

    @Synchronized
    fun start(context: Context) {
        if (_scanning.value) { main.removeCallbacks(stopper); main.postDelayed(stopper, SCAN_MS); return }
        val app = context.applicationContext
        val mgr = app.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = mgr
        try {
            val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
            lock = wifi.createMulticastLock("good-utilities-print").apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) {}
        services.clear(); queue.clear(); resolving = false
        _printers.value = emptyList()
        _scanning.value = true
        for (type in listOf("_ipp._tcp", "_ipps._tcp")) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onServiceFound(info: NsdServiceInfo) { resolve(info) }
                override fun onServiceLost(info: NsdServiceInfo) { lost(info) }
            }
            try { mgr.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l } catch (_: Exception) {}
        }
        main.postDelayed(stopper, SCAN_MS)
    }

    @Synchronized
    fun stop() {
        main.removeCallbacks(stopper)
        val mgr = nsd
        if (mgr != null) for (l in listeners) try { mgr.stopServiceDiscovery(l) } catch (_: Exception) {}
        listeners.clear()
        try { lock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        lock = null
        queue.clear(); resolving = false
        _scanning.value = false
    }

    private fun lost(info: NsdServiceInfo) {
        val k = info.serviceName + "|" + info.serviceType.trim('.').substringBefore(".local")
        synchronized(this) { services.remove(k) }
        publish()
    }

    private fun resolve(info: NsdServiceInfo) {
        val mgr = nsd ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                    if (accept(serviceInfo)) try { mgr.unregisterServiceInfoCallback(this) } catch (_: Exception) {}
                }
                override fun onServiceLost() {}
                override fun onServiceInfoCallbackUnregistered() {}
            }
            try { mgr.registerServiceInfoCallback(info, mainExecutor, cb) } catch (_: Exception) {}
        } else {
            synchronized(this) { queue.addLast(info) }
            pump()
        }
    }

    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun pump() {
        val mgr = nsd ?: return
        val next = synchronized(this) {
            if (resolving) return
            val n = queue.removeFirstOrNull() ?: return
            resolving = true
            n
        }
        val done = Runnable { synchronized(this) { resolving = false }; pump() }
        val guard = Runnable { done.run() }
        main.postDelayed(guard, 6000)
        try {
            mgr.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) { main.removeCallbacks(guard); done.run() }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) { accept(serviceInfo); main.removeCallbacks(guard); done.run() }
            })
        } catch (_: Exception) { main.removeCallbacks(guard); done.run() }
    }

    /** Returns true when the info was complete enough to use. */
    @SuppressLint("NewApi")
    @Suppress("DEPRECATION")
    private fun accept(info: NsdServiceInfo): Boolean {
        val addrs: List<InetAddress> = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
        val addr = addrs.firstOrNull { it is Inet4Address }
            ?: addrs.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
            ?: return false
        val attrs: Map<String, String> = info.attributes.mapValues { (_, v) -> v?.toString(Charsets.UTF_8).orEmpty() }
        val type = info.serviceType.orEmpty()
        val tls = type.contains("_ipps")
        val hostStr = (addr.hostAddress ?: return false).substringBefore('%')
        val rp = (attrs["rp"] ?: "ipp/print").trim('/')
        val pdl = attrs["pdl"]?.split(',')?.map { it.trim() }.orEmpty()
        val uuid = attrs["UUID"]?.takeIf { it.isNotBlank() }?.lowercase()
        val model = attrs["ty"].orEmpty().ifBlank { attrs["product"].orEmpty().trim('(', ')') }
        val found = FoundPrinter(
            key = uuid ?: hostStr,
            address = PrinterAddress(hostStr, info.port.takeIf { it > 0 } ?: 631, rp, tls),
            name = info.serviceName.orEmpty().replace("\\032", " "),
            model = model,
            note = attrs["note"].orEmpty(),
            uuid = uuid,
            color = attrs["Color"]?.let { it.equals("T", true) },
            duplex = attrs["Duplex"]?.let { it.equals("T", true) },
            pdl = pdl,
        )
        val k = info.serviceName + "|" + (if (tls) "_ipps._tcp" else "_ipp._tcp")
        synchronized(this) { services[k] = found }
        publish()
        return true
    }

    private fun publish() {
        val list = synchronized(this) { services.values.toList() }
            .groupBy { it.key }
            .map { (_, g) -> g.firstOrNull { !it.address.tls } ?: g.first() }
            .sortedBy { it.name.lowercase() }
        _printers.update { list }
    }

    private const val SCAN_MS = 60_000L
}
