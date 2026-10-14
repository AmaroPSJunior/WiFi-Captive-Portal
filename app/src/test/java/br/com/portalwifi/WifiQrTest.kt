package br.com.portalwifi

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiQrTest {
    @Test fun escaping() {
        assertEquals("WIFI:T:WPA;S:A\\;B;P:a\\:b\\,c;;", WifiQr.payload("A;B", "a:b,c"))
    }
    @Test fun normal() {
        assertEquals("WIFI:T:WPA;S:Portal;P:12345678;;", WifiQr.payload("Portal", "12345678"))
    }
}
