package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import java.time.Instant
import java.time.LocalDate

/**
 * 수험 번호 일괄 발급 결과입니다.
 */
data class ExamineeNumberIssueResult(
    val issuedCount: Int,
    val skippedCount: Int,
    val totalTargets: Int,
)

/**
 * 1차 합격자 일괄 산출 결과입니다.
 */
data class ScreeningResult(
    val dryRun: Boolean,
    val passCount: Int,
    val failCount: Int,
    val excludedCount: Int,
    val processedAt: Instant,
)

/**
 * 지원자 한 명의 최종 합격 산출 결과입니다.
 *
 * @property status 산출된 최종 상태. 산출되지 않으면 `FINAL_FAIL`
 */
data class FinalScreeningResult(
    val applicantId: Long,
    val status: ApplicantStatus,
    val processedAt: Instant,
)

/**
 * 서명된 다운로드 링크입니다.
 */
data class DownloadLink(
    val downloadUrl: String,
    val expiresAt: Instant,
)

/**
 * 날짜별 접수 건수입니다.
 */
data class DailyApplicantCount(
    val date: LocalDate,
    val count: Long,
)

/**
 * 지원 현황 통계입니다. 요청하지 않은 지표는 null로 둡니다.
 */
data class ApplicantStatistics(
    val generatedAt: Instant,
    val applicantCount: ApplicantCount? = null,
    val competitionRate: Map<AdmissionType, Double>? = null,
    val regionDistribution: Map<Region, Long>? = null,
    val typeDistribution: Map<AdmissionType, Long>? = null,
    val dailyTrend: List<DailyApplicantCount>? = null,
)

/**
 * 전체 지원자 수와 전형별 내역입니다.
 */
data class ApplicantCount(
    val total: Long,
    val byType: Map<AdmissionType, Long>,
)
