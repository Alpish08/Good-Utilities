package com.tools.app.print

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/** Pure IPP (RFC 8010) binary encoding and decoding. No Android dependencies, so it is JVM-testable. */
object Ipp {
    // Delimiter tags
    const val TAG_OPERATION = 0x01
    const val TAG_JOB = 0x02
    const val TAG_END = 0x03
    const val TAG_PRINTER = 0x04
    const val TAG_UNSUPPORTED = 0x05

    // Value tags
    const val T_INTEGER = 0x21
    const val T_BOOLEAN = 0x22
    const val T_ENUM = 0x23
    const val T_OCTETS = 0x30
    const val T_DATETIME = 0x31
    const val T_RESOLUTION = 0x32
    const val T_RANGE = 0x33
    const val T_BEGIN_COLLECTION = 0x34
    const val T_TEXT_LANG = 0x35
    const val T_NAME_LANG = 0x36
    const val T_END_COLLECTION = 0x37
    const val T_TEXT = 0x41
    const val T_NAME = 0x42
    const val T_KEYWORD = 0x44
    const val T_URI = 0x45
    const val T_URI_SCHEME = 0x46
    const val T_CHARSET = 0x47
    const val T_LANGUAGE = 0x48
    const val T_MIME = 0x49
    const val T_MEMBER_NAME = 0x4A

    // Operations
    const val OP_PRINT_JOB = 0x0002
    const val OP_CANCEL_JOB = 0x0008
    const val OP_GET_JOB_ATTRIBUTES = 0x0009
    const val OP_GET_PRINTER_ATTRIBUTES = 0x000B

    // Status codes
    const val STATUS_OK = 0x0000
    const val STATUS_OK_IGNORED = 0x0001
    const val STATUS_OK_CONFLICTING = 0x0002
    const val STATUS_CLIENT_NOT_FOUND = 0x0406
    const val STATUS_VERSION_NOT_SUPPORTED = 0x0503

    fun isSuccess(status: Int) = status < 0x0100

    // Job states
    const val JOB_PENDING = 3
    const val JOB_HELD = 4
    const val JOB_PROCESSING = 5
    const val JOB_STOPPED = 6
    const val JOB_CANCELED = 7
    const val JOB_ABORTED = 8
    const val JOB_COMPLETED = 9

    // Printer states
    const val PRINTER_IDLE = 3
    const val PRINTER_PROCESSING = 4
    const val PRINTER_STOPPED = 5

    // ---- values ------------------------------------------------------------------------------------------------

    class Value(val tag: Int, val bytes: ByteArray) {
        val int: Int? get() = if (bytes.size == 4 && (tag == T_INTEGER || tag == T_ENUM)) ByteBuffer.wrap(bytes).int else null
        val bool: Boolean? get() = if (tag == T_BOOLEAN && bytes.size == 1) bytes[0].toInt() != 0 else null
        val string: String get() = if (tag in 0x40..0x4F || tag == T_MEMBER_NAME) String(bytes, StandardCharsets.UTF_8) else when (tag) {
            T_TEXT_LANG, T_NAME_LANG -> decodeLangString(bytes)
            else -> String(bytes, StandardCharsets.UTF_8)
        }
        val resolution: Resolution? get() = if (tag == T_RESOLUTION && bytes.size == 9) {
            val b = ByteBuffer.wrap(bytes); Resolution(b.int, b.int, b.get().toInt())
        } else null
        val range: IntRange? get() = if (tag == T_RANGE && bytes.size == 8) {
            val b = ByteBuffer.wrap(bytes); b.int..b.int
        } else null
        override fun toString() = int?.toString() ?: bool?.toString() ?: resolution?.toString() ?: range?.toString() ?: string
    }

    /** x/y resolution; [units] 3 = dots per inch, 4 = dots per cm. */
    data class Resolution(val x: Int, val y: Int, val units: Int) {
        val dpiX get() = if (units == 4) Math.round(x * 2.54f) else x
        val dpiY get() = if (units == 4) Math.round(y * 2.54f) else y
    }

    private fun decodeLangString(b: ByteArray): String {
        if (b.size < 4) return String(b, StandardCharsets.UTF_8)
        val bb = ByteBuffer.wrap(b)
        val l1 = bb.short.toInt() and 0xFFFF
        if (2 + l1 + 2 > b.size) return String(b, StandardCharsets.UTF_8)
        bb.position(2 + l1)
        val l2 = bb.short.toInt() and 0xFFFF
        if (4 + l1 + l2 > b.size) return String(b, StandardCharsets.UTF_8)
        return String(b, 4 + l1, l2, StandardCharsets.UTF_8)
    }

    fun int(v: Int) = Value(T_INTEGER, ByteBuffer.allocate(4).putInt(v).array())
    fun enum(v: Int) = Value(T_ENUM, ByteBuffer.allocate(4).putInt(v).array())
    fun bool(v: Boolean) = Value(T_BOOLEAN, byteArrayOf(if (v) 1 else 0))
    fun keyword(s: String) = Value(T_KEYWORD, s.toByteArray(StandardCharsets.UTF_8))
    fun text(s: String) = Value(T_TEXT, s.toByteArray(StandardCharsets.UTF_8))
    fun name(s: String) = Value(T_NAME, s.toByteArray(StandardCharsets.UTF_8))
    fun uri(s: String) = Value(T_URI, s.toByteArray(StandardCharsets.UTF_8))
    fun charset(s: String) = Value(T_CHARSET, s.toByteArray(StandardCharsets.UTF_8))
    fun language(s: String) = Value(T_LANGUAGE, s.toByteArray(StandardCharsets.UTF_8))
    fun mime(s: String) = Value(T_MIME, s.toByteArray(StandardCharsets.UTF_8))
    fun range(lo: Int, hi: Int) = Value(T_RANGE, ByteBuffer.allocate(8).putInt(lo).putInt(hi).array())
    fun resolution(x: Int, y: Int, units: Int = 3) =
        Value(T_RESOLUTION, ByteBuffer.allocate(9).putInt(x).putInt(y).put(units.toByte()).array())

    class Attribute(val tag: Int, val name: String, val values: List<Value>) {
        constructor(tag: Int, name: String, value: Value) : this(tag, name, listOf(value))
    }

    class Group(val tag: Int, val attributes: List<Attribute>) {
        operator fun get(name: String): Attribute? = attributes.firstOrNull { it.name == name }
        fun strings(name: String): List<String> = this[name]?.values?.filter { it.tag >= 0x40 }?.map { it.string }.orEmpty()
        fun string(name: String): String? = strings(name).firstOrNull()
        fun ints(name: String): List<Int> = this[name]?.values?.mapNotNull { it.int }.orEmpty()
        fun int(name: String): Int? = ints(name).firstOrNull()
        fun bool(name: String): Boolean? = this[name]?.values?.firstOrNull()?.bool
        fun resolutions(name: String): List<Resolution> = this[name]?.values?.mapNotNull { it.resolution }.orEmpty()
        fun ranges(name: String): List<IntRange> = this[name]?.values?.mapNotNull { it.range }.orEmpty()
    }

    /** [code] is the operation id in a request and the status code in a response. */
    class Message(val major: Int, val minor: Int, val code: Int, val requestId: Int, val groups: List<Group>) {
        fun group(tag: Int): Group? = groups.firstOrNull { it.tag == tag }
        val operation get() = group(TAG_OPERATION)
        val printer get() = group(TAG_PRINTER)
        val job get() = group(TAG_JOB)
        val statusMessage get() = operation?.string("status-message")
    }

    // ---- encoding -----------------------------------------------------------------------------------------------

    /** Header + attribute groups + end-of-attributes tag. The document data (if any) follows directly after. */
    fun encodeRequest(major: Int, minor: Int, op: Int, requestId: Int, groups: List<Group>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(major); out.write(minor)
        out.write(op shr 8); out.write(op and 0xFF)
        writeInt(out, requestId)
        for (g in groups) {
            out.write(g.tag)
            for (a in g.attributes) {
                a.values.forEachIndexed { i, v ->
                    out.write(v.tag)
                    if (i == 0) {
                        val n = a.name.toByteArray(StandardCharsets.UTF_8)
                        writeShort(out, n.size); out.write(n)
                    } else writeShort(out, 0)
                    writeShort(out, v.bytes.size); out.write(v.bytes)
                }
            }
        }
        out.write(TAG_END)
        return out.toByteArray()
    }

    private fun writeInt(o: ByteArrayOutputStream, v: Int) { o.write(v ushr 24); o.write(v ushr 16); o.write(v ushr 8); o.write(v) }
    private fun writeShort(o: ByteArrayOutputStream, v: Int) { o.write(v shr 8); o.write(v) }

    /** The mandatory operation attributes that open every request. */
    fun operationGroup(printerUri: String?, vararg more: Attribute): Group {
        val list = mutableListOf(
            Attribute(T_CHARSET, "attributes-charset", charset("utf-8")),
            Attribute(T_LANGUAGE, "attributes-natural-language", language("en")),
        )
        if (printerUri != null) list += Attribute(T_URI, "printer-uri", uri(printerUri))
        list += more
        return Group(TAG_OPERATION, list)
    }

    // ---- decoding -----------------------------------------------------------------------------------------------

    class DecodeException(message: String) : Exception(message)

    /** Decodes a response (or request) message. Trailing document data after end-of-attributes is ignored. */
    fun decode(data: ByteArray): Message {
        if (data.size < 8) throw DecodeException("Response too short")
        val b = ByteBuffer.wrap(data)
        val major = b.get().toInt() and 0xFF
        val minor = b.get().toInt() and 0xFF
        val code = b.short.toInt() and 0xFFFF
        val reqId = b.int
        val groups = ArrayList<Group>()
        var groupTag = -1
        var attrs = ArrayList<Attribute>()
        var curName: String? = null
        var curTag = 0
        var curValues = ArrayList<Value>()
        var depth = 0 // collection nesting; collection contents are skipped

        fun flushAttr() {
            if (curName != null) attrs += Attribute(curTag, curName!!, curValues)
            curName = null; curValues = ArrayList()
        }
        fun flushGroup() {
            flushAttr()
            if (groupTag >= 0) groups += Group(groupTag, attrs)
            attrs = ArrayList()
        }

        loop@ while (b.hasRemaining()) {
            val tag = b.get().toInt() and 0xFF
            if (tag <= 0x05) {
                if (tag == TAG_END) { flushGroup(); groupTag = -1; break@loop }
                flushGroup(); groupTag = tag
                continue
            }
            if (b.remaining() < 2) throw DecodeException("Truncated attribute")
            val nameLen = b.short.toInt() and 0xFFFF
            if (b.remaining() < nameLen + 2) throw DecodeException("Truncated attribute name")
            val nameBytes = ByteArray(nameLen).also { b.get(it) }
            val valLen = b.short.toInt() and 0xFFFF
            if (b.remaining() < valLen) throw DecodeException("Truncated attribute value")
            val valBytes = ByteArray(valLen).also { b.get(it) }

            if (tag == T_BEGIN_COLLECTION) {
                if (depth == 0) {
                    if (nameLen > 0) { flushAttr(); curName = String(nameBytes, StandardCharsets.UTF_8); curTag = tag }
                    curValues += Value(tag, ByteArray(0))
                }
                depth++
                continue
            }
            if (tag == T_END_COLLECTION) { if (depth > 0) depth--; continue }
            if (depth > 0) continue // member names / values of a collection

            if (nameLen > 0) {
                flushAttr()
                curName = String(nameBytes, StandardCharsets.UTF_8); curTag = tag
            } else if (curName == null) throw DecodeException("Value without attribute")
            curValues += Value(tag, valBytes)
        }
        flushGroup()
        return Message(major, minor, code, reqId, groups)
    }
}
