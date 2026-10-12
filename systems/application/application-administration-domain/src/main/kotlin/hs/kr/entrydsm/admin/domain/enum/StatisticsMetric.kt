package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원 현황 통계에서 조회할 수 있는 지표입니다.
 */
enum class StatisticsMetric {
    APPLICANT_COUNT,
    COMPETITION_RATE,
    /** 전형별 1순위 선발 정원(기본 정원 × 배수, 올림). 전형 공통 후순위 정원은 포함하지 않는다. */
    FIRST_PASS_QUOTA,
    GENDER_RATIO,
    REGION_DISTRIBUTION,
    TYPE_DISTRIBUTION,
    DAILY_TREND,
}
