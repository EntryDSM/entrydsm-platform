package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.model.Applicant
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentPassCalculatorTest {
    private val calculator = DocumentPassCalculator()

    @Test
    fun `선발 정원과 경쟁률 정원은 후순위의 일반전형 포함 여부가 다르다`() {
        assertEquals(mapOf(AdmissionType.REGULAR to 64, AdmissionType.SOCIAL to 4, AdmissionType.MEISTER to 20), calculator.firstPassQuotas())
        assertEquals(mapOf(AdmissionType.REGULAR to 104, AdmissionType.SOCIAL to 4, AdmissionType.MEISTER to 20), calculator.competitionQuotas())
    }

    @Test
    fun `전체 128명까지는 전형 분포와 관계없이 전원 합격한다`() {
        for (count in listOf(0, 64, 128)) {
            assertEquals(count, passed((1L..count.toLong()).map { applicant(it) }).size)
        }
        assertEquals(104, passed((1L..129L).map { applicant(it) }).size)
    }

    @Test
    fun `전체 정원 이내이면 성적과 전형이 없는 지원자도 전원 합격한다`() {
        val applicants = (1L..128L).map { Applicant(id = it, accountId = it) }
        assertEquals((1L..128L).toSet(), passed(applicants))
    }

    @Test
    fun `129명부터 선발하고 탈락자를 포함해 모든 지원자의 결과를 반환한다`() {
        val result = calculator.calculate((1L..129L).map { applicant(it) })
        assertEquals((1L..129L).toSet(), result.keys)
        assertEquals((1L..104L).toSet(), result.filterValues { it == PassResultStatus.PASS }.keys)
        assertEquals((105L..129L).toSet(), result.filterValues { it == PassResultStatus.FAIL }.keys)
    }

    @Test
    fun `대전 우선 선발 후 전형별 1순위와 전형 무관 후순위를 선발한다`() {
        val applicants = buildList {
            addAll((1L..80L).map { applicant(it, score = 300.0 - it) })
            addAll((81L..120L).map { applicant(it, region = Region.DAEJEON, score = 0.0) })
            addAll((121L..150L).map { applicant(it, AdmissionType.MEISTER, score = 500.0) })
            addAll((151L..160L).map { applicant(it, AdmissionType.SOCIAL, score = 1000.0) })
        }
        // 1순위: 일반 64(대전 32 포함), 마이스터 20, 사회통합 4.
        // 후순위: 남은 사회통합 6, 마이스터 10, 일반 전국 24.
        assertEquals(((1L..56L) + (81L..112L) + (121L..160L)).toSet(), passed(applicants))
    }

    @Test
    fun `특별전형 미달은 전원 합격하고 후순위는 40명만 선발한다`() {
        val applicants = (1L..150L).map { applicant(it) } +
            (151L..156L).map { applicant(it, AdmissionType.MEISTER, score = 0.0) } +
            (157L..158L).map { applicant(it, AdmissionType.SOCIAL, score = 0.0) }
        assertEquals(((1L..104L) + (151L..158L)).toSet(), passed(applicants))
    }

    @Test
    fun `일반전형 미달도 전원 합격하고 남은 마이스터에서 후순위를 선발한다`() {
        val applicants = (1L..150L).map { applicant(it, AdmissionType.MEISTER) } +
            (151L..155L).map { applicant(it, score = 0.0) } +
            (156L..157L).map { applicant(it, AdmissionType.SOCIAL, score = 0.0) }
        assertEquals(((1L..60L) + (151L..157L)).toSet(), passed(applicants))
    }

    @Test
    fun `대전 지원자가 부족하면 일반 정원을 다른 지역 성적순으로 채운다`() {
        val applicants = (1L..145L).map { applicant(it) } +
            (146L..150L).map { applicant(it, region = Region.DAEJEON, score = 0.0) }
        assertEquals(((1L..99L) + (146L..150L)).toSet(), passed(applicants))
    }

    @Test
    fun `대전 우선 정원 32명의 직전과 동일과 초과 경계를 지킨다`() {
        val national = (1L..150L).map { applicant(it) }
        val daejeon = (151L..183L).map { applicant(it, region = Region.DAEJEON, score = 200.0 - it) }
        assertEquals(((1L..73L) + (151L..181L)).toSet(), passed(national + daejeon.take(31)))
        assertEquals(((1L..72L) + (151L..182L)).toSet(), passed(national + daejeon.take(32)))
        assertEquals(((1L..72L) + (151L..182L)).toSet(), passed(national + daejeon))
    }

    @Test
    fun `특별전형 정원과 지원자 수가 같으면 낮은 성적이어도 전원 합격한다`() {
        val applicants = (1L..150L).map { applicant(it) } +
            (151L..170L).map { applicant(it, AdmissionType.MEISTER, score = 0.0) } +
            (171L..174L).map { applicant(it, AdmissionType.SOCIAL, score = 0.0) }
        assertEquals(((1L..104L) + (151L..174L)).toSet(), passed(applicants))
    }

    @Test
    fun `동점은 지원자 번호순으로 선발하고 입력 순서에 영향받지 않는다`() {
        val applicants = (1L..150L).map { applicant(it, score = 10.0) }
        assertEquals((1L..104L).toSet(), passed(applicants.reversed()))
    }

    private fun passed(applicants: List<Applicant>): Set<Long> =
        calculator.calculate(applicants).filterValues { it == PassResultStatus.PASS }.keys

    private fun applicant(
        id: Long,
        admissionType: AdmissionType = AdmissionType.REGULAR,
        region: Region = Region.NATIONAL,
        score: Double = 1000.0 - id,
    ) = Applicant(id = id, accountId = id, admissionType = admissionType, region = region, totalScore = score)
}
