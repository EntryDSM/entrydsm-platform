package hs.kr.entrydsm.application.domain.model

import hs.kr.entrydsm.application.domain.enum.AdmissionType

/**
 * 성적 산출 내역입니다.
 *
 * 항목 점수는 전형별 반영 비율을 곱하기 전의 원점수이고, 총점만 전형별로 갈립니다.
 *
 * @property subjectScore 교과 원점수
 * @property attendanceScore 출석 점수
 * @property volunteerScore 봉사 점수
 * @property totalByAdmissionType 전형별 총점. 가산점까지 더해 상한으로 자른 값
 */
data class ScoreBreakdown(
    val subjectScore: Double,
    val attendanceScore: Double,
    val volunteerScore: Double,
    val totalByAdmissionType: Map<AdmissionType, Double>,
)
