package com.miftah.core.net

import org.junit.Assert.*
import org.junit.Test

class NetTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    private fun query(name: String, id: Int = 0x1234, type: Int = 1): ByteArray {
        val labels = name.split(".")
        val body = ArrayList<Byte>()
        labels.forEach { l -> body += l.length.toByte(); l.forEach { body += it.code.toByte() } }
        body += 0
        body += (type shr 8).toByte(); body += type.toByte(); body += 0; body += 1
        val header = byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0)
        return header + body.toByteArray()
    }

    @Test fun ipHeaderChecksumKnownVector() {
        val h = hex("45 00 00 73 00 00 40 00 40 11 00 00 c0 a8 00 01 c0 a8 00 c7")
        assertEquals(0xB861, Ipv4Udp.checksum(h, 0, 20))
    }

    @Test fun udpRoundTripAndValidChecksums() {
        val src = Ipv4Udp.ip(10, 255, 255, 2)
        val dst = Ipv4Udp.ip(10, 255, 255, 1)
        val payload = query("example.com")
        val pkt = Ipv4Udp.build(src, dst, 40000, 53, payload)
        assertEquals(0, Ipv4Udp.checksum(pkt, 0, 20))
        val d = Ipv4Udp.parse(pkt)!!
        assertEquals(src, d.src); assertEquals(dst, d.dst)
        assertEquals(40000, d.srcPort); assertEquals(53, d.dstPort)
        assertArrayEquals(payload, d.payload)
        assertEquals(0, Ipv4Udp.udpChecksum(src, dst, pkt, 20, pkt.size - 20))
    }

    @Test fun parseRejectsBadPackets() {
        assertNull(Ipv4Udp.parse(ByteArray(10)))
        val pkt = Ipv4Udp.build(1, 2, 1, 2, ByteArray(4))
        val tcp = pkt.copyOf(); tcp[9] = 6
        assertNull(Ipv4Udp.parse(tcp))
        val v6 = pkt.copyOf(); v6[0] = 0x65
        assertNull(Ipv4Udp.parse(v6))
        val frag = pkt.copyOf(); frag[7] = 0x10
        assertNull(Ipv4Udp.parse(frag))
        assertNull(Ipv4Udp.parse(pkt, pkt.size - 3))
    }

    @Test fun dnsQuestionParsing() {
        val q = DnsPacket.parseQuestion(query("Ads.Example.COM", id = 0xBEEF, type = 28))!!
        assertEquals("ads.example.com", q.name); assertEquals(0xBEEF, q.id); assertEquals(28, q.type)
        assertNull(DnsPacket.parseQuestion(ByteArray(5)))
        val resp = query("a.com"); resp[2] = 0x81.toByte()
        assertNull(DnsPacket.parseQuestion(resp))
        val compressed = query("a.com"); compressed[12] = 0xC0.toByte()
        assertNull(DnsPacket.parseQuestion(compressed))
        val truncated = query("a.com").copyOf(14)
        assertNull(DnsPacket.parseQuestion(truncated))
    }

    @Test fun nxdomainResponse() {
        val raw = query("ads.example.com", id = 0x0102)
        val q = DnsPacket.parseQuestion(raw)!!
        val r = DnsPacket.nxdomain(raw, q)
        assertEquals(0x01, r[0].toInt()); assertEquals(0x02, r[1].toInt())
        assertEquals(0x81.toByte(), r[2])
        assertEquals(0x83.toByte(), r[3])
        assertEquals(1, r[5].toInt())
        assertEquals(0, r[7].toInt()); assertEquals(0, r[9].toInt()); assertEquals(0, r[11].toInt())
        assertEquals(q.questionEnd, r.size)
    }

    @Test fun blocklistSubdomainsAllowAndTld() {
        val b = Blocklist(listOf("doubleclick.net", "Ads.Tracker.IO."), allowed = listOf("good.doubleclick.net"))
        assertTrue(b.isBlocked("doubleclick.net"))
        assertTrue(b.isBlocked("x.y.doubleclick.net"))
        assertFalse(b.isBlocked("good.doubleclick.net"))
        assertFalse(b.isBlocked("sub.good.doubleclick.net"))
        assertTrue(b.isBlocked("ads.tracker.io"))
        assertFalse(b.isBlocked("tracker.io"))
        assertFalse(b.isBlocked("example.com"))
        assertFalse(Blocklist(listOf("com")).isBlocked("example.com"))
        assertFalse(b.isBlocked(""))
    }

    @Test fun parserHandlesHostsAdblockAndPlain() {
        val text = """
            # comment
            ! another
            0.0.0.0 ads.one.com two-ads.net # inline
            127.0.0.1 localhost
            ||three.org^
            ||bad.org/path^
            plain.example.io
            1.2.3.4
            not a domain here
            UPPER.CASE.COM
        """.trimIndent()
        val set = BlocklistParser.parse(text)
        assertEquals(setOf("ads.one.com", "two-ads.net", "three.org", "plain.example.io", "upper.case.com"), set)
        assertEquals("a.b.com", BlocklistParser.normalizeDomain("A.B.com."))
        assertNull(BlocklistParser.normalizeDomain("nodot"))
        assertNull(BlocklistParser.normalizeDomain("1.2.3.4"))
        assertNull(BlocklistParser.normalizeDomain("bad domain.com"))
        assertEquals(2, BlocklistParser.parse("a.com\nb.com\nc.com", maxEntries = 2).size)
    }
}
