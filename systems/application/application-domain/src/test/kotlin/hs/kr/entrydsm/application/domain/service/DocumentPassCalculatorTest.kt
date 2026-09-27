package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.model.Applicant
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentPassCalculatorTest {
    private val calculator = DocumentPassCalculator()

    @Test
    fun passesEveryoneWhenApplicantCountIsAtMost128() {
        val applicants = (1L..128L).map { applicant(it, AdmissionType.REGULAR, 0.0) }

        assertEquals(128, calculator.calculate(applicants).values.count { it == PassResultStatus.PASS })
    }

    @Test
    fun passesTwiceEachAdmissionQuotaWhenApplicantCountExceeds128() {
        val applicants = buildList {
            addAll((1L..65L).map { applicant(it, AdmissionType.REGULAR, it.toDouble()) })
            addAll((66L..98L).map { applicant(it, AdmissionType.MEISTER, it.toDouble()) })
            addAll((99L..131L).map { applicant(it, AdmissionType.SOCIAL, it.toDouble()) })
        }
        val results = calculator.calculate(applicants)

        assertEquals(128, results.values.count { it == PassResultStatus.PASS })
        assertEquals(PassResultStatus.FAIL, results.getValue(1L))
        assertEquals(PassResultStatus.FAIL, results.getValue(66L))
        assertEquals(PassResultStatus.FAIL, results.getValue(99L))
    }

    private fun applicant(id: Long, admissionType: AdmissionType, score: Double) =
        Applicant(id = id, accountId = id, admissionType = admissionType, totalScore = score)
}
