package hs.kr.entrydsm.application

import hs.kr.entrydsm.application.config.InstitutionCodeInitializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationBootstrapApplicationTest {
    @Test
    fun contextLoads() {
        assertTrue(true)
    }

    @Test
    fun parsesQuotedInstitutionCodeCsvValues() {
        assertEquals(
            listOf("7010977", "서울특별시교육청, 국악학교", "국악\"학교", "7010000", "중학교", "현존", ""),
            InstitutionCodeInitializer.parseCsvLine("7010977,\"서울특별시교육청, 국악학교\",\"국악\"\"학교\",7010000,중학교,현존,"),
        )
    }
}
