package com.ykatchou.ylauncher.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrContentTest {

    @Test
    fun `links are recognised by scheme`() {
        assertTrue(QrContent.parse("https://example.com/a?b=1") is QrContent.Link)
        assertTrue(QrContent.parse("HTTP://example.com") is QrContent.Link)
    }

    @Test
    fun `wifi code yields ssid password and security in any field order`() {
        val wifi = QrContent.parse("WIFI:S:Casa 5G;T:WPA;P:segredo123;;") as QrContent.Wifi
        assertEquals("Casa 5G", wifi.ssid)
        assertEquals("segredo123", wifi.password)
        assertEquals("WPA", wifi.security)
        assertEquals(false, wifi.hidden)
    }

    @Test
    fun `wifi values honour backslash escapes`() {
        val wifi = QrContent.parse("""WIFI:T:WPA;S:a\;b;P:x\:y\\z;H:true;;""") as QrContent.Wifi
        assertEquals("a;b", wifi.ssid)
        assertEquals("""x:y\z""", wifi.password)
        assertEquals(true, wifi.hidden)
    }

    @Test
    fun `open wifi has no password`() {
        val wifi = QrContent.parse("WIFI:T:nopass;S:Visitas;;") as QrContent.Wifi
        assertEquals(null, wifi.password)
    }

    @Test
    fun `wifi without ssid falls back to text`() {
        assertTrue(QrContent.parse("WIFI:T:WPA;P:x;;") is QrContent.Text)
    }

    @Test
    fun `pix payload is recognised`() {
        assertTrue(QrContent.parse("00020126580014br.gov.bcb.pix0136abc") is QrContent.Pix)
    }

    @Test
    fun `ponte pairing code carries address token and name`() {
        val p = QrContent.parse("hashi://192.168.0.9:8738?t=abc123&n=MacBook%20Pro") as QrContent.Ponte
        assertEquals("192.168.0.9", p.host)
        assertEquals(8738, p.port)
        assertEquals("abc123", p.token)
        assertEquals("MacBook Pro", p.name)
    }

    @Test
    fun `ponte code without token is just text`() {
        assertTrue(QrContent.parse("hashi://192.168.0.9:8738") is QrContent.Text)
    }

    @Test
    fun `anything else is text`() {
        assertTrue(QrContent.parse("olá mundo") is QrContent.Text)
    }
}
