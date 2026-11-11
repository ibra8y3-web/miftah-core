package com.miftah.core.net

class UdpDatagram(val src: Int, val dst: Int, val srcPort: Int, val dstPort: Int, val payload: ByteArray)

/** Minimal IPv4/UDP codec for the local DNS filter. Anything unexpected is rejected (null), never guessed. */
object Ipv4Udp {
    fun ip(a: Int, b: Int, c: Int, d: Int): Int = (a shl 24) or (b shl 16) or (c shl 8) or d

    private fun u8(p: ByteArray, i: Int) = p[i].toInt() and 0xFF
    private fun u16(p: ByteArray, i: Int) = (u8(p, i) shl 8) or u8(p, i + 1)
    private fun i32(p: ByteArray, i: Int) = (u8(p, i) shl 24) or (u8(p, i + 1) shl 16) or (u8(p, i + 2) shl 8) or u8(p, i + 3)
    private fun put16(p: ByteArray, i: Int, v: Int) { p[i] = (v shr 8).toByte(); p[i + 1] = v.toByte() }
    private fun put32(p: ByteArray, i: Int, v: Int) { put16(p, i, v ushr 16); put16(p, i + 2, v and 0xFFFF) }

    fun parse(packet: ByteArray, length: Int = packet.size): UdpDatagram? {
        if (length < 28 || length > packet.size) return null
        val b0 = u8(packet, 0)
        if (b0 shr 4 != 4) return null
        val ihl = (b0 and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null
        val total = u16(packet, 2)
        if (total < ihl + 8 || total > length) return null
        if (u8(packet, 9) != 17) return null
        val frag = u16(packet, 6)
        if ((frag and 0x1FFF) != 0 || (frag and 0x2000) != 0) return null // fragments are not handled
        val udpLen = u16(packet, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > total) return null
        return UdpDatagram(
            src = i32(packet, 12), dst = i32(packet, 16),
            srcPort = u16(packet, ihl), dstPort = u16(packet, ihl + 2),
            payload = packet.copyOfRange(ihl + 8, ihl + udpLen),
        )
    }

    fun build(src: Int, dst: Int, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        require(total <= 65535) { "packet too large" }
        val out = ByteArray(total)
        out[0] = 0x45
        put16(out, 2, total)
        put16(out, 6, 0x4000)
        out[8] = 64
        out[9] = 17
        put32(out, 12, src)
        put32(out, 16, dst)
        put16(out, 10, checksum(out, 0, 20))
        put16(out, 20, srcPort)
        put16(out, 22, dstPort)
        put16(out, 24, udpLen)
        System.arraycopy(payload, 0, out, 28, payload.size)
        val ck = udpChecksum(src, dst, out, 20, udpLen)
        put16(out, 26, if (ck == 0) 0xFFFF else ck)
        return out
    }

    private fun sum(data: ByteArray, off: Int, len: Int, initial: Long): Long {
        var s = initial
        var i = off
        val end = off + len
        while (i + 1 < end) { s += (u8(data, i) shl 8) or u8(data, i + 1); i += 2 }
        if (i < end) s += u8(data, i) shl 8
        return s
    }

    private fun fold(sum0: Long): Int {
        var s = sum0
        while (s shr 16 != 0L) s = (s and 0xFFFF) + (s shr 16)
        return (s.inv() and 0xFFFF).toInt()
    }

    fun checksum(data: ByteArray, off: Int, len: Int): Int = fold(sum(data, off, len, 0))

    fun udpChecksum(src: Int, dst: Int, data: ByteArray, off: Int, len: Int): Int {
        var s = (src ushr 16).toLong() + (src and 0xFFFF) + (dst ushr 16) + (dst and 0xFFFF) + 17 + len
        s = sum(data, off, len, s)
        return fold(s)
    }
}
