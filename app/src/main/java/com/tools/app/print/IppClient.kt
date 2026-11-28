package com.tools.app.print

import com.tools.app.R
import com.tools.app.ui.t
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Where a printer lives. [path] has no leading slash ("ipp/print"). */
data class PrinterAddress(val host: String, val port: Int = 631, val path: String = "ipp/print", val tls: Boolean = false) {
    val ippUri get() = "${if (tls) "ipps" else "ipp"}://${hostForUri()}:$port/$path"
    val httpUrl get() = "${if (tls) "https" else "http"}://${hostForUri()}:$port/$path"
    private fun hostForUri() = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    val key get() = "$host:$port/$path"
}

class IppException(message: String, val status: Int = -1, val http: Int = -1) : IOException(message)

/** One marker (toner / ink) from marker-levels etc. [level] is 0..100, or -1 when unknown. */
data class Marker(val name: String, val color: Int?, val level: Int, val type: String)

data class PrinterCaps(
    val makeModel: String = "",
    val name: String = "",
    val state: Int = Ipp.PRINTER_IDLE,
    val stateReasons: List<String> = emptyList(),
    val stateMessage: String = "",
    val acceptingJobs: Boolean = true,
    val formats: List<String> = emptyList(),
    val mediaSupported: List<String> = emptyList(),
    val mediaDefault: String? = null,
    val mediaReady: List<String> = emptyList(),
    val sides: List<String> = emptyList(),
    val colorModes: List<String> = emptyList(),
    val qualities: List<Int> = emptyList(),
    val copiesMax: Int = 1,
    val copiesKnown: Boolean = false,
    val pageRanges: Boolean = false,
    val printScaling: List<String> = emptyList(),
    val resolutions: List<Ipp.Resolution> = emptyList(),
    val pwgResolutions: List<Ipp.Resolution> = emptyList(),
    val pwgTypes: List<String> = emptyList(),
    val pwgSheetBack: String? = null,
    val urf: List<String> = emptyList(),
    val markers: List<Marker> = emptyList(),
    /** Which IPP version answered (2.0 or 1.1) so later requests skip the fallback. */
    val ippMajor: Int = 2,
    val ippMinor: Int = 0,
) {
    val supportsColor: Boolean get() =
        colorModes.any { it == "color" } || urf.any { it.startsWith("SRGB", true) } || pwgTypes.any { it.startsWith("srgb") }
    val duplexSupported get() = sides.any { it.startsWith("two-sided") }
    val canPdf get() = formats.any { it.equals("application/pdf", true) }
    val canPwg get() = formats.any { it.equals("image/pwg-raster", true) }
    val canUrf get() = formats.any { it.equals("image/urf", true) } || urf.isNotEmpty()
    val canJpeg get() = formats.any { it.equals("image/jpeg", true) }
    val known get() = formats.isNotEmpty()

    /** Raster resolutions from the RS token of urf-supported. */
    val urfResolutions: List<Int> get() = urf.filter { it.startsWith("RS") }.flatMap { t ->
        t.removePrefix("RS").split('-', ',').mapNotNull { it.toIntOrNull() }
    }.distinct().sorted()
}

object IppClient {
    private val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    })
    private val sslFactory by lazy { SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }.socketFactory }
    private val anyHost = HostnameVerifier { _, _ -> true }

    private var reqId = 1
    private fun nextId() = synchronized(this) { reqId++ }

    /** Prefer the Wi-Fi / Ethernet network: Android may otherwise route LAN traffic over mobile data on a Wi-Fi without internet. */
    private fun lanNetwork(context: Context?): Network? {
        if (context == null) return null
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks.firstOrNull {
                val c = cm.getNetworkCapabilities(it)
                c != null && (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
            }
        } catch (_: Exception) { null }
    }

    private fun open(context: Context?, a: PrinterAddress, length: Long, connectTimeout: Int, readTimeout: Int): HttpURLConnection {
        val url = URL(a.httpUrl)
        val net = lanNetwork(context)
        val conn = (net?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        if (conn is HttpsURLConnection) { conn.sslSocketFactory = sslFactory; conn.hostnameVerifier = anyHost }
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = connectTimeout
        conn.readTimeout = readTimeout
        conn.useCaches = false
        conn.setRequestProperty("Content-Type", "application/ipp")
        conn.setRequestProperty("Connection", "close")
        conn.setRequestProperty("User-Agent", "GoodUtilities-Print/1.0")
        conn.setFixedLengthStreamingMode(length)
        return conn
    }

    /**
     * Sends one IPP request (header bytes, then optionally the document file) and returns the decoded response.
     * [onSent] reports 0..1 of the document body uploaded.
     */
    fun send(
        context: Context?, a: PrinterAddress, header: ByteArray, body: File? = null,
        connectTimeout: Int = 5000, readTimeout: Int = 30000, onSent: ((Float) -> Unit)? = null,
    ): Ipp.Message {
        val length = header.size + (body?.length() ?: 0L)
        val conn = open(context, a, length, connectTimeout, readTimeout)
        try {
            try {
                conn.outputStream.use { os ->
                    os.write(header)
                    if (body != null) {
                        val total = body.length().coerceAtLeast(1)
                        var sent = 0L
                        body.inputStream().buffered(64 * 1024).use { ins ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = ins.read(buf)
                                if (n < 0) break
                                os.write(buf, 0, n)
                                sent += n
                                onSent?.invoke(sent.toFloat() / total)
                            }
                        }
                    }
                    os.flush()
                }
            } catch (e: IOException) {
                // Some printers answer (e.g. with an error) before reading everything; try to read that answer.
                val code = try { conn.responseCode } catch (_: Exception) { -1 }
                if (code < 0) throw e
            }
            val code = conn.responseCode
            if (code == 426) throw IppException(t(R.string.print_this_printer_needs_an_encrypted_connec), http = code)
            if (code != 200) throw IppException(t(R.string.print_the_printer_answered_http, code), http = code)
            val bytes = conn.inputStream.use { it.readBytes() }
            return Ipp.decode(bytes)
        } finally {
            conn.disconnect()
        }
    }

    // ---- operations ---------------------------------------------------------------------------------------------

    val requestedPrinterAttributes = listOf(
        "printer-make-and-model", "printer-name", "printer-info", "printer-state", "printer-state-reasons", "printer-state-message",
        "printer-is-accepting-jobs", "document-format-supported", "media-supported", "media-default", "media-ready",
        "sides-supported", "print-color-mode-supported", "print-quality-supported", "copies-supported", "page-ranges-supported",
        "print-scaling-supported", "printer-resolution-supported", "pwg-raster-document-resolution-supported",
        "pwg-raster-document-type-supported", "pwg-raster-document-sheet-back", "urf-supported",
        "marker-levels", "marker-names", "marker-colors", "marker-types",
    )

    private fun attempt(
        context: Context?, a: PrinterAddress, op: Int, major: Int, minor: Int, groups: List<Ipp.Group>,
        body: File?, readTimeout: Int, onSent: ((Float) -> Unit)?,
    ): Ipp.Message {
        val header = Ipp.encodeRequest(major, minor, op, nextId(), groups)
        return send(context, a, header, body, readTimeout = readTimeout, onSent = onSent)
    }

    /** Sends [op] trying IPP 2.0 first and falling back to 1.1 when the printer reports version-not-supported. */
    fun call(
        context: Context?, a: PrinterAddress, op: Int, groups: List<Ipp.Group>, body: File? = null,
        preferred: Pair<Int, Int> = 2 to 0, readTimeout: Int = 30000, onSent: ((Float) -> Unit)? = null,
    ): Ipp.Message {
        val first = try {
            attempt(context, a, op, preferred.first, preferred.second, groups, body, readTimeout, onSent)
        } catch (e: IppException) {
            // A few old devices reject 2.0 with an HTTP error; retry once with 1.1 (only for body-less requests).
            if (preferred.first == 1 || body != null) throw e
            return attempt(context, a, op, 1, 1, groups, body, readTimeout, onSent)
        }
        if (first.code == Ipp.STATUS_VERSION_NOT_SUPPORTED && preferred.first != 1) {
            return attempt(context, a, op, 1, 1, groups, body, readTimeout, onSent)
        }
        return first
    }

    fun getPrinterAttributes(context: Context?, a: PrinterAddress): PrinterCaps {
        val groups = listOf(
            Ipp.operationGroup(
                a.ippUri,
                Ipp.Attribute(Ipp.T_NAME, "requesting-user-name", Ipp.name("Good Utilities")),
                Ipp.Attribute(Ipp.T_KEYWORD, "requested-attributes", requestedPrinterAttributes.map { Ipp.keyword(it) }),
            ),
        )
        val resp = call(context, a, Ipp.OP_GET_PRINTER_ATTRIBUTES, groups, readTimeout = 10000)
        if (!Ipp.isSuccess(resp.code)) throw IppException(resp.statusMessage ?: t(R.string.print_the_printer_refused_the_request_0x, resp.code.toString(16)), resp.code)
        val p = resp.printer ?: throw IppException(t(R.string.print_the_printer_did_not_send_its_attribute))
        return parseCaps(p, resp.major, resp.minor)
    }

    fun parseCaps(p: Ipp.Group, major: Int = 2, minor: Int = 0): PrinterCaps {
        val levels = p.ints("marker-levels")
        val names = p.strings("marker-names")
        val colors = p.strings("marker-colors")
        val types = p.strings("marker-types")
        val markers = levels.mapIndexed { i, l ->
            Marker(
                // Printers often report short codes ("BK", "C"…); show them as words.
                name = names.getOrNull(i).orEmpty().let { n ->
                    when (n.trim().uppercase()) {
                        "BK", "K", "BLACK" -> t(R.string.print_black); "C" -> t(R.string.print_cyan); "M" -> t(R.string.print_magenta); "Y" -> t(R.string.print_yellow)
                        else -> n
                    }
                },
                color = parseColor(colors.getOrNull(i)),
                level = if (l in 0..100) l else -1,
                type = types.getOrNull(i).orEmpty(),
            )
        }
        val copies = p.ranges("copies-supported").firstOrNull()
        return PrinterCaps(
            makeModel = p.string("printer-make-and-model").orEmpty(),
            name = p.string("printer-name").orEmpty(),
            state = p.int("printer-state") ?: Ipp.PRINTER_IDLE,
            stateReasons = p.strings("printer-state-reasons"),
            stateMessage = p.string("printer-state-message").orEmpty(),
            acceptingJobs = p.bool("printer-is-accepting-jobs") ?: true,
            formats = p.strings("document-format-supported"),
            mediaSupported = p.strings("media-supported"),
            mediaDefault = p.string("media-default"),
            mediaReady = p.strings("media-ready"),
            sides = p.strings("sides-supported"),
            colorModes = p.strings("print-color-mode-supported"),
            qualities = p.ints("print-quality-supported"),
            copiesMax = copies?.last ?: 1,
            copiesKnown = copies != null,
            pageRanges = p.bool("page-ranges-supported") ?: false,
            printScaling = p.strings("print-scaling-supported"),
            resolutions = p.resolutions("printer-resolution-supported"),
            pwgResolutions = p.resolutions("pwg-raster-document-resolution-supported"),
            pwgTypes = p.strings("pwg-raster-document-type-supported"),
            pwgSheetBack = p.string("pwg-raster-document-sheet-back"),
            urf = p.strings("urf-supported"),
            markers = markers,
            ippMajor = major, ippMinor = minor,
        )
    }

    private fun parseColor(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val t = s.trim().split(';').first().trim()
        if (t.startsWith("#") && t.length >= 7) return t.substring(1, 7).toIntOrNull(16)
        return when (t.lowercase()) {
            "black" -> 0x000000; "cyan" -> 0x00AEEF; "magenta" -> 0xEC008C; "yellow" -> 0xFFF200
            else -> null
        }
    }
}
