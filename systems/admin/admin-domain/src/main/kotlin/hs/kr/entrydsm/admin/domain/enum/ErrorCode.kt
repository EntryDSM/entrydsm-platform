package hs.kr.entrydsm.admin.domain.enum

/**
 * Admin API에서 클라이언트에 노출하는 오류 코드입니다.
 *
 * @property status 오류에 대응하는 HTTP 상태 코드
 * @property message 클라이언트에 전달할 기본 오류 메시지
 */
enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    INVALID_REQUEST_BODY(400, "요청 본문이 올바르지 않습니다."),
    INVALID_SCORE_POLICY(400, "성적 정책 가중치의 합은 1이어야 합니다."),
    INVALID_STATISTICS_METRIC(400, "지원하지 않는 통계 지표입니다."),
    AUTH_UNAUTHORIZED(401, "인증이 필요합니다."),
    ACCESS_DENIED(403, "관리자 권한이 없습니다."),
    APPLICANT_NOT_FOUND(404, "지원자를 찾을 수 없습니다."),
    SCORE_POLICY_NOT_FOUND(404, "등록된 성적 정책이 없습니다."),
    EXPORT_JOB_NOT_FOUND(404, "Export 작업을 찾을 수 없습니다."),
    APPLICATION_DOCUMENT_NOT_FOUND(404, "제출된 원서 원본이 없습니다."),
    INVALID_STATUS_TRANSITION(409, "현재 상태에서는 변경할 수 없는 상태입니다."),
    EXAMINEE_NUMBER_NOT_ISSUED(409, "수험 번호가 발급되지 않은 지원자입니다."),
    EXPORT_NOT_COMPLETED(409, "아직 완료되지 않은 Export 작업입니다."),
    ADMISSION_TICKET_GENERATION_FAILED(500, "수험표 생성에 실패했습니다."),
    STORAGE_UNAVAILABLE(500, "파일 저장소를 사용할 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
}
