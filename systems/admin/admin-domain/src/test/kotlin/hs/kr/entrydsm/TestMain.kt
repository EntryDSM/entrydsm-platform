package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDomainModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun `환경별 저장소 루트를 만든다`() {
        assertEquals("dsm_Entry/backend/prod/", DocumentNaming.keyRoot("prod"))
        assertEquals("dsm_Entry/backend/stag/", DocumentNaming.keyRoot("stag"))
    }

    @Test
    fun `지원하지 않는 저장소 환경은 거부한다`() {
        assertThrows(IllegalArgumentException::class.java) { DocumentNaming.keyRoot("") }
        assertThrows(IllegalArgumentException::class.java) { DocumentNaming.keyRoot("dev") }
    }
}
