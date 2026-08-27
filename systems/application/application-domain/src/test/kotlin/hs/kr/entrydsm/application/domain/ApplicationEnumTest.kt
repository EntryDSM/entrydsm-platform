package hs.kr.entrydsm.application.domain

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.GuardianRelation
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.ResultType
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationEnumTest {
    @Test
    fun admissionTypeContainsExpectedValues() {
        assertEquals(
            listOf("REGULAR", "MEISTER", "SOCIAL"),
            AdmissionType.entries.map { it.name },
        )
    }

    @Test
    fun graduationTypeContainsExpectedValues() {
        assertEquals(
            listOf("PROSPECTIVE", "GRADUATED", "GED"),
            GraduationType.entries.map { it.name },
        )
    }

    @Test
    fun guardianRelationContainsExpectedValues() {
        assertEquals(
            listOf("FATHER", "MOTHER", "OTHER"),
            GuardianRelation.entries.map { it.name },
        )
    }

    @Test
    fun passResultStatusContainsExpectedValues() {
        assertEquals(
            listOf("PASS", "FAIL", "PENDING"),
            PassResultStatus.entries.map { it.name },
        )
    }

    @Test
    fun regionContainsExpectedValues() {
        assertEquals(
            listOf("DAEJEON", "NATIONAL"),
            Region.entries.map { it.name },
        )
    }

    @Test
    fun resultTypeContainsExpectedValues() {
        assertEquals(
            listOf("DOCUMENT", "FINAL"),
            ResultType.entries.map { it.name },
        )
    }
}
