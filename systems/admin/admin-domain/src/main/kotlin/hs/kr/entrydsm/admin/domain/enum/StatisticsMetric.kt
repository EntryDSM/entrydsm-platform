package hs.kr.entrydsm.admin.domain.enum

/**
 * 지원 현황 통계에서 조회할 수 있는 지표입니다.
 */
enum class StatisticsMetric {
    APPLICANT_COUNT,
    COMPETITION_RATE,
    /** 전형별 1차 선발 인원(모집 정원 × 1차 배수, 올림). 1차 합격자 산출이 쓰는 정원과 같다. */
    FIRST_PASS_QUOTA,
    GENDER_RATIO,
    REGION_DISTRIBUTION,
    TYPE_DISTRIBUTION,
    DAILY_TREND,
}
