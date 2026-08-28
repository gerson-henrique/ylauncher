package com.ykatchou.ylauncher.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IpOwnerTest {

    @Test
    fun `maps well-known ipv4 ranges to their owner`() {
        assertEquals("Google", IpOwner.ownerOf("142.250.79.14"))
        assertEquals("Google", IpOwner.ownerOf("8.8.8.8"))
        assertEquals("Meta", IpOwner.ownerOf("157.240.1.35"))
        assertEquals("Meta", IpOwner.ownerOf("57.144.67.32"))
        assertEquals("Cloudflare", IpOwner.ownerOf("104.16.5.5"))
        assertEquals("Apple", IpOwner.ownerOf("17.253.5.1"))
    }

    @Test
    fun `maps ipv6 on the 32-bit prefix`() {
        assertEquals("Google", IpOwner.ownerOf("2607:f8b0:400c:c3d::be"))
        assertEquals("Meta", IpOwner.ownerOf("2a03:2880:f362:121:face:b00c:0:167"))
        assertEquals("Cloudflare", IpOwner.ownerOf("2606:4700:3033::ac43:a1b2"))
    }

    @Test
    fun `labels private ranges as local`() {
        assertEquals("rede local", IpOwner.ownerOf("192.168.0.1"))
        assertEquals("rede local", IpOwner.ownerOf("10.4.5.6"))
        assertEquals("rede local", IpOwner.ownerOf("fe80::1"))
    }

    @Test
    fun `returns null for an unknown address`() {
        assertNull(IpOwner.ownerOf("203.0.113.7"))
        assertNull(IpOwner.ownerOf("2400:abcd::1"))
    }
}
