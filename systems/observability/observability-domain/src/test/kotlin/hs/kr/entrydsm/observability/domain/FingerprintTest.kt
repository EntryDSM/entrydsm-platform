package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.service.Fingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {
    @Test
    fun sameInputProducesSameFingerprint() {
        assertEquals(Fingerprint.of("DOM", "boom", "/a"), Fingerprint.of("DOM", "boom", "/a"))
    }

    @Test
    fun differentInputProducesDifferentFingerprint() {
        assertNotEquals(Fingerprint.of("DOM", "boom", "/a"), Fingerprint.of("DOM", "boom", "/b"))
    }

    @Test
    fun fingerprintIsEightHexChars() {
        val fingerprint = Fingerprint.of("DOM", "boom", "/a")
        assertEquals(8, fingerprint.length)
        assertEquals(true, fingerprint.matches(Regex("[0-9a-f]{8}")))
    }
}
