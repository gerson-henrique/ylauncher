package com.ykatchou.ylauncher.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcNetParserTest {

    @Test
    fun `parses an established ipv4 tcp connection with little-endian address`() {
        // rem 0805A8C0:01BB -> 192.168.5.8:443, state 01 (ESTABLISHED), uid 10123
        val dump = """
            #tcp
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 0100007F:1F90 0805A8C0:01BB 01 00000000:00000000 00:00000000 00000000 10123        0 12345 1 0
        """.trimIndent()
        val conns = ProcNetParser.parse(dump)
        assertEquals(1, conns.size)
        assertEquals("192.168.5.8", conns[0].remoteIp)
        assertEquals(443, conns[0].remotePort)
        assertEquals(10123, conns[0].uid)
        assertEquals("tcp", conns[0].proto)
    }

    @Test
    fun `skips listeners, loopback and unbound remotes`() {
        val dump = """
            #tcp
               0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000 0 1 1 0
               1: 0100007F:0035 0100007F:C000 01 00000000:00000000 00:00000000 00000000  1000 0 2 1 0
        """.trimIndent()
        // First is LISTEN (0A) + unbound; second talks to 127.0.0.1 (loopback). Both dropped.
        assertTrue(ProcNetParser.parse(dump).isEmpty())
    }

    @Test
    fun `collapses an ipv4-mapped ipv6 address back to dotted`() {
        // ::ffff:8.8.4.4  ->  bytes 00*10, ff ff, 08 08 04 04, stored as 4 LE words
        val mapped = "00000000" + "00000000" + "ffff0000" + "04040808"
        val dump = "#tcp6\n 0: 00000000000000000000000000000000:1F90 ${mapped}:01BB 01 x x x 10222 0 1 1 0"
        val conns = ProcNetParser.parse(dump)
        assertEquals(1, conns.size)
        assertEquals("8.8.4.4", conns[0].remoteIp)
    }

    @Test
    fun `keeps a connected udp socket (quic) and labels the proto`() {
        val dump = "#udp6\n 0: 00000000000000000000000000000000:C000 0805A8C0:01BB 07 x x x 10333 0 1 1 0"
        val conns = ProcNetParser.parse(dump)
        assertEquals(1, conns.size)
        assertEquals("udp", conns[0].proto)
        assertEquals(443, conns[0].remotePort)
    }

    @Test
    fun `survives empty and header-only input`() {
        assertTrue(ProcNetParser.parse("").isEmpty())
        assertTrue(ProcNetParser.parse("#tcp\n  sl  local_address rem_address   st").isEmpty())
    }
}
