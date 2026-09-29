package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantCount
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.DailyApplicantCount
import hs.kr.entrydsm.admin.domain.model.GenderRatio
import hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase
import hs.kr.entrydsm.admin.domain.port.out.AdmissionQuotaRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val COMPETITION_RATE_SCALE = 2
private const val RATIO_SCALE = 3
private val KOREA_ZONE = ZoneId.of("Asia/Seoul")

@Service
@Transactional(readOnly = true)
class StatisticsService(
    private val applicantRepository: ApplicantRepository,
    private val admissionQuotaRepository: AdmissionQuotaRepository,
    private val clock: Clock,
    @Value("\${admin.screening.first-pass-multiplier}") private val firstPassMultiplier: Double,
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
                // 정원이 없으면 빈 맵이다. 경쟁률과 같다.
                admissionQuotaRepository.find()?.scaled(firstPassMultiplier).orEmpty()
            },
            genderRatio = metrics.ifRequested(StatisticsMetric.GENDER_RATIO) {
                genderRatio(applicants)
            },
            regionDistribution = metrics.ifRequested(StatisticsMetric.REGION_DISTRIBUTION) {
                // 0명인 시·도도 enum 순서대로 채워 키 17개를 늘 내려준다.
                val counts = applicants.countBy { residenceRegion(it.address) }
                ResidenceRegion.entries.associateWith { counts[it] ?: 0L }
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

    /**
     * 주소 첫 토큰(시·도)으로 매깁니다. 원서 주소는 Daum 우편번호의 도로명 주소라 `(34503) 대전 유성구 …` 처럼
     * 우편번호가 앞에 붙고 시·도가 축약형입니다. 포함 검사를 하면 `경기 광주시` 가 광주로 잡혀 첫 토큰만 봅니다.
     * 알아볼 수 없는 주소는 null 이라 시·도 분포에서 빠집니다. 총 지원자 수에는 그대로 듭니다.
     */
    private fun residenceRegion(address: String?): ResidenceRegion? {
        val sido = address.orEmpty().trim().substringAfter(") ").substringBefore(' ')
        return REGION_NAMES[sido]
    }

    /** 지역·전형이 비어 있는 원서는 분포에 넣을 칸이 없어 뺀다. 총 지원자 수에는 그대로 든다. */
    private fun <K : Any> List<Applicant>.countBy(key: (Applicant) -> K?): Map<K, Long> =
        mapNotNull(key).groupingBy { it }.eachCount().mapValues { it.value.toLong() }

    private fun <T> Set<StatisticsMetric>.ifRequested(
        metric: StatisticsMetric,
        block: () -> T,
    ): T? = if (metric in this) block() else null

    private companion object {
        // 정식 명칭과 Daum 축약형을 모두 받는다.
        val REGION_NAMES = mapOf(
            ResidenceRegion.SEOUL to listOf("서울특별시", "서울"),
            ResidenceRegion.BUSAN to listOf("부산광역시", "부산"),
            ResidenceRegion.DAEGU to listOf("대구광역시", "대구"),
            ResidenceRegion.INCHEON to listOf("인천광역시", "인천"),
            ResidenceRegion.GWANGJU to listOf("광주광역시", "광주"),
            ResidenceRegion.DAEJEON to listOf("대전광역시", "대전"),
            ResidenceRegion.ULSAN to listOf("울산광역시", "울산"),
            ResidenceRegion.SEJONG to listOf("세종특별자치시", "세종"),
            ResidenceRegion.GYEONGGI to listOf("경기도", "경기"),
            ResidenceRegion.GANGWON to listOf("강원특별자치도", "강원도", "강원"),
            ResidenceRegion.CHUNGBUK to listOf("충청북도", "충북"),
            ResidenceRegion.CHUNGNAM to listOf("충청남도", "충남"),
            ResidenceRegion.JEONBUK to listOf("전북특별자치도", "전라북도", "전북"),
            ResidenceRegion.JEONNAM to listOf("전라남도", "전남"),
            ResidenceRegion.GYEONGBUK to listOf("경상북도", "경북"),
            ResidenceRegion.GYEONGNAM to listOf("경상남도", "경남"),
            ResidenceRegion.JEJU to listOf("제주특별자치도", "제주도", "제주"),
        ).flatMap { (region, names) -> names.map { it to region } }.toMap()
    }
}
