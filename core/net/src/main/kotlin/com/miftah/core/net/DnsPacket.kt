package com.miftah.core.net

class DnsQuestion(val id: Int, val name: String, val type: Int, val questionEnd: Int)

object DnsPacket {
    private fun u16(p: ByteArray, i: Int) = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
    private fun put16(p: ByteArray, i: Int, v: Int) { p[i] = (v shr 8).toByte(); p[i + 1] = v.toByte() }

    /** Parses a standard single-question query. Responses, compressed names and odd shapes return null. */
    fun parseQuestion(msg: ByteArray): DnsQuestion? {
        if (msg.size < 12) return null
        if (u16(msg, 2) and 0x8000 != 0) return null
        if (u16(msg, 4) != 1) return null
        var pos = 12
        val labels = ArrayList<String>()
        var total = 0
        while (true) {
            if (pos >= msg.size) return null
            val len = msg[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if (len and 0xC0 != 0 || len > 63 || pos + 1 + len > msg.size) return null
            total += len + 1
            if (total > 255) return null
            labels += String(msg, pos + 1, len, Charsets.US_ASCII).lowercase()
            pos += 1 + len
        }
        if (pos + 4 > msg.size) return null
        val type = u16(msg, pos)
        return DnsQuestion(u16(msg, 0), labels.joinToString("."), type, pos + 4)
    }

    /** Standard "no such domain" reply echoing the question. */
    fun nxdomain(query: ByteArray, q: DnsQuestion): ByteArray {
        val out = query.copyOfRange(0, q.questionEnd)
        val flags = u16(query, 2)
        put16(out, 2, 0x8000 or (flags and 0x7800) or (flags and 0x0100) or 0x0080 or 0x0003)
        put16(out, 6, 0)
        put16(out, 8, 0)
        put16(out, 10, 0)
        return out
    }
}
