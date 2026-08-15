package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import hs.kr.entrydsm.admin.domain.policy.ExamineeNumberPolicy
import hs.kr.entrydsm.admin.domain.policy.ScreeningPolicy
import hs.kr.entrydsm.admin.domain.policy.ScreeningStage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminPolicyTest {

    private fun applicant(
        receiptNumber: Int,
        isSubmitted: Boolean = true,
        examineeNumber: String? = null,
        totalScore: Double? = null,
        status: ApplicantStatus = ApplicantStatus.PENDING,
    ) = Applicant(
        id = receiptNumber.toLong(),
        receiptNumber = receiptNumber,
        name = "지원자$receiptNumber",
        birthDate = LocalDate.of(2010, 3, 15),
        phoneNumber = "010-0000-0000",
        region = Region.DAEJEON,
        admissionType = AdmissionType.MEISTER,
        graduationStatus = GraduationStatus.EXPECTED,
        schoolName = "대전중학교",
        examineeNumber = examineeNumber,
        isSubmitted = isSubmitted,
        status = status,
        score = totalScore?.let { ApplicantScore(0.0, 0.0, 0.0, it) },
    )

    @Test
    fun `원서가 도착한 지원자에게만 접수 번호 순으로 수험 번호를 발급한다`() {
        val result = ExamineeNumberPolicy.issue(
            listOf(
                applicant(receiptNumber = 3),
                applicant(receiptNumber = 1),
                applicant(receiptNumber = 2, isSubmitted = false),
            ),
        )

        assertEquals(listOf("100001", "100002"), result.issued.map { it.examineeNumber })
        assertEquals(listOf(1, 3), result.issued.map { it.receiptNumber })
        assertEquals(2, result.totalTargets)
    }

    @Test
    fun `이미 수험 번호가 있는 지원자는 건너뛰고 다음 번호부터 이어 발급한다`() {
        val result = ExamineeNumberPolicy.issue(
            listOf(
                applicant(receiptNumber = 1, examineeNumber = "100001"),
                applicant(receiptNumber = 2),
            ),
        )

        assertEquals(1, result.skippedCount)
        assertEquals(listOf("100002"), result.issued.map { it.examineeNumber })
    }

    @Test
    fun `1차 산출은 총점 순으로 정원까지 합격시키고 나머지는 불합격 처리한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(receiptNumber = 1, examineeNumber = "100001", totalScore = 80.0),
                applicant(receiptNumber = 2, examineeNumber = "100002", totalScore = 95.0),
                applicant(receiptNumber = 3, examineeNumber = "100003", totalScore = 90.0),
            ),
            stage = ScreeningStage.FIRST,
            quota = 2,
        )

        assertEquals(listOf(2, 3), outcome.passed.map { it.receiptNumber })
        assertEquals(listOf(1), outcome.failed.map { it.receiptNumber })
        assertTrue(outcome.passed.all { it.status == ApplicantStatus.FIRST_PASS })
        assertTrue(outcome.failed.all { it.status == ApplicantStatus.FIRST_FAIL })
    }

    @Test
    fun `동점이면 접수 번호가 빠른 지원자를 우선 합격시킨다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(receiptNumber = 5, examineeNumber = "100005", totalScore = 90.0),
                applicant(receiptNumber = 4, examineeNumber = "100004", totalScore = 90.0),
            ),
            stage = ScreeningStage.FIRST,
            quota = 1,
        )

        assertEquals(listOf(4), outcome.passed.map { it.receiptNumber })
    }

    @Test
    fun `원서 미도착이나 수험 번호 미발급 지원자는 산출에서 제외한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(receiptNumber = 1, isSubmitted = false, totalScore = 99.0),
                applicant(receiptNumber = 2, examineeNumber = null, totalScore = 99.0),
                applicant(receiptNumber = 3, examineeNumber = "100003", totalScore = null),
                applicant(receiptNumber = 4, examineeNumber = "100004", totalScore = 70.0),
            ),
            stage = ScreeningStage.FIRST,
            quota = 10,
        )

        assertEquals(listOf(1, 2, 3), outcome.excluded.map { it.receiptNumber })
        assertEquals(listOf(4), outcome.passed.map { it.receiptNumber })
    }

    @Test
    fun `최종 산출은 1차 합격자만 대상으로 한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(receiptNumber = 1, examineeNumber = "100001", totalScore = 99.0),
                applicant(
                    receiptNumber = 2,
                    examineeNumber = "100002",
                    totalScore = 70.0,
                    status = ApplicantStatus.FIRST_PASS,
                ),
            ),
            stage = ScreeningStage.FINAL,
            quota = 10,
        )

        assertEquals(listOf(2), outcome.passed.map { it.receiptNumber })
        assertTrue(outcome.passed.all { it.status == ApplicantStatus.FINAL_PASS })
    }

    @Test
    fun `정상 흐름을 벗어나는 상태 전이는 거부한다`() {
        assertTrue(ApplicantStatus.PENDING.canTransitionTo(ApplicantStatus.FIRST_PASS))
        assertTrue(ApplicantStatus.FIRST_PASS.canTransitionTo(ApplicantStatus.FINAL_PASS))
        assertFalse(ApplicantStatus.PENDING.canTransitionTo(ApplicantStatus.FINAL_PASS))
        assertFalse(ApplicantStatus.FIRST_FAIL.canTransitionTo(ApplicantStatus.FINAL_PASS))
    }
}
