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
    INVALID_ADMISSION_QUOTA(400, "모집 지역과 전형별 정원이 모두 0 이상으로 채워져야 합니다."),
    AUTH_UNAUTHORIZED(401, "인증이 필요합니다."),
    ACCESS_DENIED(403, "관리자 권한이 없습니다."),
    API_NOT_FOUND(404, "존재하지 않는 API입니다."),
    APPLICANT_NOT_FOUND(404, "지원자를 찾을 수 없습니다."),
    SCORE_POLICY_NOT_FOUND(404, "등록된 성적 정책이 없습니다."),
    ADMISSION_QUOTA_NOT_FOUND(404, "등록된 모집 정원이 없습니다."),
    EXPORT_JOB_NOT_FOUND(404, "Export 작업을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(404, "질문을 찾을 수 없습니다."),
    NOTICE_NOT_FOUND(404, "공지를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 HTTP 메서드입니다."),
    INVALID_STATUS_TRANSITION(409, "현재 상태에서는 변경할 수 없는 상태입니다."),
    EXAMINEE_NUMBER_NOT_ISSUED(409, "수험 번호가 발급되지 않은 지원자입니다."),
    EXPORT_NOT_COMPLETED(409, "아직 완료되지 않은 Export 작업입니다."),
    ADMISSION_TICKET_NO_TARGET(409, "수험표를 발급할 1차 합격자가 없습니다."),
    EXAMINEE_NUMBER_LIMIT_EXCEEDED(409, "같은 전형·지역의 수험 번호는 999개까지 발급할 수 있습니다."),
    ADMISSION_TICKET_GENERATION_FAILED(500, "수험표 생성에 실패했습니다."),
    STORAGE_UNAVAILABLE(500, "파일 저장소를 사용할 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
    NOTIFICATION_SERVICE_UNAVAILABLE(503, "알림 서비스를 일시적으로 사용할 수 없습니다."),
    APPLICATION_SERVICE_UNAVAILABLE(503, "원서 서비스를 일시적으로 사용할 수 없습니다."),
    DISTANCE_SERVICE_UNAVAILABLE(503, "거리 계산 서비스를 일시적으로 사용할 수 없습니다."),
}
