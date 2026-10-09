package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantCount
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.DailyApplicantCount
import hs.kr.entrydsm.admin.domain.model.GenderRatio
import hs.kr.entrydsm.admin.domain.model.RegionStatus
import hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import hs.kr.entrydsm.application.domain.service.DocumentPassCalculator

private const val COMPETITION_RATE_SCALE = 2
private const val RATIO_SCALE = 3
private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
// 원본 조회는 누락된 총점을 재산출하여 저장할 수 있다.
@Transactional
class StatisticsService(
    private val applicantRepository: ApplicantRepository,
    private val admissionQuotaRepository: AdmissionQuotaRepository,
    private val clock: Clock,
) : ReadStatisticsUseCase {

    /**
     * 요청한 지표를 한 번의 조회로 모두 집계합니다.
     *
     * ponytail: 지표별 GROUP BY 대신 메모리에서 집계한다. 한 회차 지원자가 수천 명
     * 규모라 충분하다. 만 단위로 커지면 application 에 집계 RPC 를 더한다.
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
            firstPassQuota = metrics.ifRequested(StatisticsMetric.FIRST_PASS_QUOTA) {
                DocumentPassCalculator().firstPassQuotas()
                    .mapKeys { (type, _) ->
                        when (type) {
                            hs.kr.entrydsm.application.domain.enum.AdmissionType.REGULAR -> AdmissionType.GENERAL
                            hs.kr.entrydsm.application.domain.enum.AdmissionType.MEISTER -> AdmissionType.MEISTER
                            hs.kr.entrydsm.application.domain.enum.AdmissionType.SOCIAL -> AdmissionType.SOCIAL
                        }
                    }
            },
            genderRatio = metrics.ifRequested(StatisticsMetric.GENDER_RATIO) {
                genderRatio(applicants)
            },
            regionDistribution = metrics.ifRequested(StatisticsMetric.REGION_DISTRIBUTION) {
                regionDistribution(applicants)
            },
            typeDistribution = metrics.ifRequested(StatisticsMetric.TYPE_DISTRIBUTION) {
                countByType
            },
            dailyTrend = metrics.ifRequested(StatisticsMetric.DAILY_TREND) {
                // 원서를 제출한 날 기준이다. 원본(우편) 도착일과 다르다.
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
     * 전형별 지원자 수를 모집 정원으로 나눕니다. 정원이 없거나 0인 전형은 건너뜁니다.
     */
    private fun competitionRate(countByType: Map<AdmissionType, Long>): Map<AdmissionType, Double> =
        admissionQuotaRepository.find()?.quotas.orEmpty()
            .filterValues { it > 0 }
            .mapValues { (type, quota) ->
                BigDecimal.valueOf(countByType[type] ?: 0L)
                    .divide(BigDecimal.valueOf(quota.toLong()), COMPETITION_RATE_SCALE, RoundingMode.HALF_UP)
                    .toDouble()
            }

    private fun genderRatio(applicants: List<Applicant>): GenderRatio {
        val byGender = applicants.countBy { it.gender }
        val total = applicants.size.toLong()
        return GenderRatio(
            total = total,
            byGender = byGender,
            maleRatio = if (total == 0L) 0.0 else BigDecimal.valueOf(
                (byGender[Gender.MALE] ?: 0L).toDouble() / total,
            ).setScale(RATIO_SCALE, RoundingMode.HALF_UP).toDouble(),
            byType = applicants
                .filter { it.admissionType != null && it.gender != null }
                .groupBy { it.admissionType!! }
                .mapValues { (_, values) -> values.countBy { it.gender } },
        )
    }

    private fun regionDistribution(applicants: List<Applicant>): RegionStatus = RegionStatus(
        total = applicants.size.toLong(),
        byScope = applicants.countBy {
            when (it.region) {
                Region.DAEJEON -> "LOCAL"
                Region.NATIONWIDE -> "NATIONWIDE"
                null -> null
            }
        },
        byRegion = applicants.groupingBy { residenceRegion(it.address) }.eachCount().mapValues { it.value.toLong() },
    )

    private fun residenceRegion(address: String?): ResidenceRegion {
        val province = address.orEmpty().trim().takeWhile { !it.isWhitespace() }
        return REGION_NAMES[province] ?: ResidenceRegion.ETC
    }

    /** 지역·전형이 비어 있는 원서는 분포에 넣을 칸이 없어 뺀다. 총 지원자 수에는 그대로 든다. */
    private fun <K : Any> List<Applicant>.countBy(key: (Applicant) -> K?): Map<K, Long> =
        mapNotNull(key).groupingBy { it }.eachCount().mapValues { it.value.toLong() }

    private fun <T> Set<StatisticsMetric>.ifRequested(
        metric: StatisticsMetric,
        block: () -> T,
    ): T? = if (metric in this) block() else null

    private companion object {
        val REGION_NAMES = linkedMapOf(
            "서울특별시" to ResidenceRegion.SEOUL,
            "부산광역시" to ResidenceRegion.BUSAN,
            "대구광역시" to ResidenceRegion.DAEGU,
            "인천광역시" to ResidenceRegion.INCHEON,
            "광주광역시" to ResidenceRegion.GWANGJU,
            "대전광역시" to ResidenceRegion.DAEJEON,
            "울산광역시" to ResidenceRegion.ULSAN,
            "세종특별자치시" to ResidenceRegion.SEJONG,
            "경기도" to ResidenceRegion.GYEONGGI,
            "강원특별자치도" to ResidenceRegion.GANGWON,
            "강원도" to ResidenceRegion.GANGWON,
            "충청북도" to ResidenceRegion.CHUNGBUK,
            "충청남도" to ResidenceRegion.CHUNGNAM,
            "전북특별자치도" to ResidenceRegion.JEONBUK,
            "전라북도" to ResidenceRegion.JEONBUK,
            "전라남도" to ResidenceRegion.JEONNAM,
            "경상북도" to ResidenceRegion.GYEONGBUK,
            "경상남도" to ResidenceRegion.GYEONGNAM,
            "제주특별자치도" to ResidenceRegion.JEJU,
            "제주도" to ResidenceRegion.JEJU,
            "서울" to ResidenceRegion.SEOUL,
            "부산" to ResidenceRegion.BUSAN,
            "대구" to ResidenceRegion.DAEGU,
            "인천" to ResidenceRegion.INCHEON,
            "광주" to ResidenceRegion.GWANGJU,
            "대전" to ResidenceRegion.DAEJEON,
            "울산" to ResidenceRegion.ULSAN,
            "세종" to ResidenceRegion.SEJONG,
            "경기" to ResidenceRegion.GYEONGGI,
            "강원" to ResidenceRegion.GANGWON,
            "충북" to ResidenceRegion.CHUNGBUK,
            "충남" to ResidenceRegion.CHUNGNAM,
            "전북" to ResidenceRegion.JEONBUK,
            "전남" to ResidenceRegion.JEONNAM,
            "경북" to ResidenceRegion.GYEONGBUK,
            "경남" to ResidenceRegion.GYEONGNAM,
            "제주" to ResidenceRegion.JEJU,
        )
    }
}
