package hs.kr.entrydsm.admin.domain.model

/**
 * 지원자의 성적 산출 결과입니다.
 *
 * 각 항목 점수는 가중치를 적용하기 전의 원점수이고, 총점만 성적 정책의 가중치를
 * 곱해 반올림한 값입니다.
 */
data class ApplicantScore(
    val subjectScore: Double,
    val attendanceScore: Double,
    val volunteerScore: Double,
    val totalScore: Double,
)
