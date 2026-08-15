package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.ApplicantCount
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val COMPETITION_RATE_SCALE = 2

@Service
@Transactional(readOnly = true)
class StatisticsService(
    private val applicantRepository: ApplicantRepository,
    private val quotaProperties: AdmissionQuotaProperties,
    private val clock: Clock,
) : ReadStatisticsUseCase {

    override fun collect(metrics: Set<StatisticsMetric>): ApplicantStatistics {
        val countByType by lazy { applicantRepository.countByAdmissionType() }

        return ApplicantStatistics(
            generatedAt = Instant.now(clock),
            applicantCount = metrics.ifRequested(StatisticsMetric.APPLICANT_COUNT) {
                ApplicantCount(total = applicantRepository.countAll(), byType = countByType)
            },
            competitionRate = metrics.ifRequested(StatisticsMetric.COMPETITION_RATE) {
                competitionRate(countByType)
            },
            regionDistribution = metrics.ifRequested(StatisticsMetric.REGION_DISTRIBUTION) {
                applicantRepository.countByRegion()
            },
            typeDistribution = metrics.ifRequested(StatisticsMetric.TYPE_DISTRIBUTION) {
                countByType
            },
            dailyTrend = metrics.ifRequested(StatisticsMetric.DAILY_TREND) {
                applicantRepository.countBySubmittedDate()
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
                BigDecimal((countByType[type] ?: 0L).toDouble() / quota)
                    .setScale(COMPETITION_RATE_SCALE, RoundingMode.HALF_UP)
                    .toDouble()
            }

    private fun <T> Set<StatisticsMetric>.ifRequested(
        metric: StatisticsMetric,
        block: () -> T,
    ): T? = if (metric in this) block() else null
}
