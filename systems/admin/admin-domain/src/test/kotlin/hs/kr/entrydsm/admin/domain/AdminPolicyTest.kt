package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
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
        id: Long,
        isArrived: Boolean = true,
        examineeNumber: String? = null,
        totalScore: Double? = null,
        status: ApplicantStatus = ApplicantStatus.PENDING,
        region: Region? = Region.DAEJEON,
        admissionType: AdmissionType? = AdmissionType.MEISTER,
        address: String? = "대전광역시 유성구",
    ) = Applicant(
        id = id,
        name = "지원자$id",
        birthDate = LocalDate.of(2010, 3, 15),
        phoneNumber = "010-0000-0000",
        region = region,
        admissionType = admissionType,
        graduationStatus = GraduationStatus.EXPECTED,
        schoolName = "대전중학교",
        totalScore = totalScore,
        examineeNumber = examineeNumber,
        isArrived = isArrived,
        status = status,
        address = address,
    )

    @Test
    fun `전형과 지역별로 거리순 수험 번호를 발급하고 동률이면 지원자 번호를 사용한다`() {
        val result = ExamineeNumberPolicy.issue(
            listOf(
                applicant(id = 3L),
                applicant(id = 1L),
                applicant(id = 2L, admissionType = AdmissionType.GENERAL, region = Region.NATIONWIDE),
            ),
            mapOf(1L to 100L, 2L to 50L, 3L to 100L),
        )

        assertEquals(listOf("11001", "11002", "32001"), result.issued.map { it.examineeNumber })
        assertEquals(listOf(1L, 3L, 2L), result.issued.map { it.id })
    }

    @Test
    fun `전형과 지역 코드를 조합한다`() {
        val applicants = AdmissionType.entries.flatMap { type ->
            Region.entries.map { region -> applicant(id = type.ordinal * 10L + region.ordinal, admissionType = type, region = region) }
        }

        val result = ExamineeNumberPolicy.issue(applicants, applicants.associate { it.id to 1L })

        assertEquals(setOf("31001", "32001", "11001", "12001", "21001", "22001"), result.issued.map { it.examineeNumber }.toSet())
    }

    @Test
    fun `미도착과 필수값 누락은 제외하고 기존 그룹 번호 다음부터 발급한다`() {
        val result = ExamineeNumberPolicy.issue(
            listOf(
                applicant(id = 1L, examineeNumber = "11007"),
                applicant(id = 2L),
                applicant(id = 3L, isArrived = false),
                applicant(id = 4L, address = null),
            ),
            mapOf(2L to 10L),
        )

        assertEquals(1, result.skippedCount)
        assertEquals(listOf("11008"), result.issued.map { it.examineeNumber })
        assertEquals(2, result.totalTargets)
    }

    @Test
    fun `형식이 잘못된 기존 번호는 덮어쓰지 않고 신규 번호 예약에서도 제외한다`() {
        val result = ExamineeNumberPolicy.issue(
            listOf(
                applicant(id = 1L, examineeNumber = "잘못된번호"),
                applicant(id = 2L),
            ),
            mapOf(2L to 10L),
        )

        assertEquals(listOf("11001"), result.issued.map { it.examineeNumber })
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `그룹 순번이 999를 넘으면 실패한다`() {
        val exception = runCatching {
            ExamineeNumberPolicy.issue(
                listOf(
                    applicant(id = 1L, examineeNumber = "11999"),
                    applicant(id = 2L),
                ),
                mapOf(2L to 10L),
            )
        }.exceptionOrNull()

        assertEquals(ErrorCode.EXAMINEE_NUMBER_LIMIT_EXCEEDED, (exception as AdminDomainException).errorCode)
    }

    /** 모든 지역 × 전형 묶음에 같은 정원을 준다. */
    private fun quotas(quota: Int): Map<Region, Map<AdmissionType, Int>> =
        Region.entries.associateWith { AdmissionType.entries.associateWith { quota } }

    @Test
    fun `합격자는 지역과 전형 묶음별로 따로 순위를 매겨 정원까지 뽑는다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(id = 1L, examineeNumber = "100001", totalScore = 95.0),
                applicant(id = 2L, examineeNumber = "100002", totalScore = 90.0),
                applicant(
                    id = 3L,
                    examineeNumber = "100003",
                    totalScore = 60.0,
                    region = Region.NATIONWIDE,
                    admissionType = AdmissionType.GENERAL,
                ),
                applicant(
                    id = 4L,
                    examineeNumber = "100004",
                    totalScore = 99.0,
                    admissionType = AdmissionType.SOCIAL,
                ),
            ),
            stage = ScreeningStage.FIRST,
            quotas = mapOf(
                Region.DAEJEON to mapOf(AdmissionType.MEISTER to 1),
                Region.NATIONWIDE to mapOf(AdmissionType.GENERAL to 1),
            ),
        )

        assertEquals(setOf(1L, 3L), outcome.passed.map { it.id }.toSet())
        assertEquals(setOf(2L, 4L), outcome.failed.map { it.id }.toSet())
    }

    @Test
    fun `1차 산출은 총점 순으로 정원까지 합격시키고 나머지는 불합격 처리한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(id = 1L, examineeNumber = "100001", totalScore = 80.0),
                applicant(id = 2L, examineeNumber = "100002", totalScore = 95.0),
                applicant(id = 3L, examineeNumber = "100003", totalScore = 90.0),
            ),
            stage = ScreeningStage.FIRST,
            quotas = quotas(2),
        )

        assertEquals(listOf(2L, 3L), outcome.passed.map { it.id })
        assertEquals(listOf(1L), outcome.failed.map { it.id })
        assertTrue(outcome.passed.all { it.status == ApplicantStatus.FIRST_PASS })
        assertTrue(outcome.failed.all { it.status == ApplicantStatus.FIRST_FAIL })
    }

    @Test
    fun `동점이면 지원자 번호가 빠른 지원자를 우선 합격시킨다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(id = 5L, examineeNumber = "100005", totalScore = 90.0),
                applicant(id = 4L, examineeNumber = "100004", totalScore = 90.0),
            ),
            stage = ScreeningStage.FIRST,
            quotas = quotas(1),
        )

        assertEquals(listOf(4L), outcome.passed.map { it.id })
    }

    @Test
    fun `원서 미도착·수험 번호 미발급·지역이나 전형이 빈 지원자는 산출에서 제외한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(id = 1L, isArrived = false, totalScore = 99.0),
                applicant(id = 2L, examineeNumber = null, totalScore = 99.0),
                applicant(id = 3L, examineeNumber = "100003", totalScore = null),
                // 제출된 원서에도 지역·전형이 비어 있을 수 있다. 묶을 칸이 없어 제외한다.
                applicant(id = 4L, examineeNumber = "100004", totalScore = 99.0, region = null),
                applicant(id = 5L, examineeNumber = "100005", totalScore = 99.0, admissionType = null),
                applicant(id = 6L, examineeNumber = "100006", totalScore = 70.0),
            ),
            stage = ScreeningStage.FIRST,
            quotas = quotas(10),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), outcome.excluded.map { it.id })
        assertEquals(listOf(6L), outcome.passed.map { it.id })
    }

    @Test
    fun `최종 산출은 1차 합격자만 대상으로 한다`() {
        val outcome = ScreeningPolicy.evaluate(
            listOf(
                applicant(id = 1L, examineeNumber = "100001", totalScore = 99.0),
                applicant(
                    id = 2L,
                    examineeNumber = "100002",
                    totalScore = 70.0,
                    status = ApplicantStatus.FIRST_PASS,
                ),
            ),
            stage = ScreeningStage.FINAL,
            quotas = quotas(10),
        )

        assertEquals(listOf(2L), outcome.passed.map { it.id })
        assertTrue(outcome.passed.all { it.status == ApplicantStatus.FINAL_PASS })
    }

    @Test
    fun `개별 최종 산출은 정원 안에 든 지원자만 합격시킨다`() {
        val first = applicant(
            id = 1L,
            examineeNumber = "100001",
            totalScore = 95.0,
            status = ApplicantStatus.FIRST_PASS,
        )
        val second = applicant(
            id = 2L,
            examineeNumber = "100002",
            totalScore = 80.0,
            status = ApplicantStatus.FIRST_PASS,
        )
        val cohort = listOf(first, second)

        assertEquals(
            ApplicantStatus.FINAL_PASS,
            ScreeningPolicy.evaluateFinal(first, cohort, quotas = quotas(1)),
        )
        assertEquals(
            ApplicantStatus.FINAL_FAIL,
            ScreeningPolicy.evaluateFinal(second, cohort, quotas = quotas(1)),
        )
    }

    @Test
    fun `개별 최종 산출에서 산출되지 않은 지원자는 불합격 처리한다`() {
        val notFirstPass = applicant(
            id = 1L,
            examineeNumber = "100001",
            totalScore = 99.0,
        )
        val noScore = applicant(
            id = 2L,
            examineeNumber = "100002",
            totalScore = null,
            status = ApplicantStatus.FIRST_PASS,
        )
        val notArrived = applicant(
            id = 3L,
            isArrived = false,
            examineeNumber = "100003",
            totalScore = 99.0,
            status = ApplicantStatus.FIRST_PASS,
        )
        val cohort = listOf(notFirstPass, noScore, notArrived)

        cohort.forEach {
            assertEquals(
                ApplicantStatus.FINAL_FAIL,
                ScreeningPolicy.evaluateFinal(it, cohort, quotas = quotas(10)),
            )
        }
    }

    @Test
    fun `정상 흐름을 벗어나는 상태 전이는 거부한다`() {
        assertTrue(ApplicantStatus.PENDING.canTransitionTo(ApplicantStatus.FIRST_PASS))
        assertTrue(ApplicantStatus.FIRST_PASS.canTransitionTo(ApplicantStatus.FINAL_PASS))
        assertFalse(ApplicantStatus.PENDING.canTransitionTo(ApplicantStatus.FINAL_PASS))
        assertFalse(ApplicantStatus.FIRST_FAIL.canTransitionTo(ApplicantStatus.FINAL_PASS))
    }
}
