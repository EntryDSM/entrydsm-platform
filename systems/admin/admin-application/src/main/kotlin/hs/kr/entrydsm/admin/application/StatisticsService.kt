package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantCount
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.DailyApplicantCount
import hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val COMPETITION_RATE_SCALE = 2
private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

@Service
@Transactional(readOnly = true)
class StatisticsService(
    private val applicantRepository: ApplicantRepository,
    private val quotaProperties: AdmissionQuotaProperties,
    private val clock: Clock,
) : ReadStatisticsUseCase {

    /**
     * 요청한 지표를 한 번의 조회로 모두 집계합니다.
     *
     * ponytail: 지표별 GROUP BY 대신 메모리에서 집계한다. 한 회차 지원자가 수천 명
     * 규모라 충분하다. 만 단위로 커지면 집계 쿼리로 내린다.
     */
    override fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics {
        val applicants by lazy { applicantRepository.findAll() }
        val countByType by lazy { applicants.countBy { it.admissionType } }

        return ApplicantStatistics(
            generatedAt = Instant.now(clock),
            applicantCount = metrics.ifRequested(StatisticsMetric.APPLICANT_COUNT) {
                ApplicantCount(total = applicants.size.toLong(), byType = countByType)
            },
            competitionRate = metrics.ifRequested(StatisticsMetric.COMPETITION_RATE) {
                competitionRate(countByType)
            },
            regionDistribution = metrics.ifRequested(StatisticsMetric.REGION_DISTRIBUTION) {
                applicants.countBy { it.region }
            },
            typeDistribution = metrics.ifRequested(StatisticsMetric.TYPE_DISTRIBUTION) {
                countByType
            },
            dailyTrend = metrics.ifRequested(StatisticsMetric.DAILY_TREND) {
                applicants
                    .mapNotNull { it.submittedAt }
                    .groupingBy { it.atZone(KOREA_ZONE).toLocalDate() }
                    .eachCount()
                    .map { (date, count) -> DailyApplicantCount(date, count.toLong()) }
                    .sortedBy { it.date }
            },
        )
    }

    /**
     * 전형별 지원자 수를 모집 정원으로 나눕니다. 정원이 설정되지 않은 전형은 건너뜁니다.
     */
    private fun competitionRate(countByType: Map<AdmissionType, Long>): Map<AdmissionType, Double> =
        quotaProperties.byType
            .filterValues { it > 0 }
            .mapValues { (type, quota) ->
                BigDecimal.valueOf((countByType[type] ?: 0L).toDouble() / quota)
                    .setScale(COMPETITION_RATE_SCALE, RoundingMode.HALF_UP)
                    .toDouble()
            }

    private fun <K> List<Applicant>.countBy(key: (Applicant) -> K): Map<K, Long> =
        groupingBy(key).eachCount().mapValues { it.value.toLong() }

    private fun <T> Set<StatisticsMetric>.ifRequested(
        metric: StatisticsMetric,
        block: () -> T,
    ): T? = if (metric in this) block() else null
}
