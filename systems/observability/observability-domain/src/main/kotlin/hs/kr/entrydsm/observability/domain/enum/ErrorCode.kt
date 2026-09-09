package hs.kr.entrydsm.observability.domain.enum

/**
 * Monitor API에서 클라이언트에 노출하는 오류 코드입니다.
 *
 * @property status 오류에 대응하는 HTTP 상태 코드
 * @property message 클라이언트에 전달할 기본 오류 메시지
 */
enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    INVALID_PAYLOAD(400, "요청 본문이 올바르지 않습니다."),
    INVALID_METRIC(400, "지원하지 않는 지표명입니다."),
    INVALID_TIME_RANGE(400, "조회 시간 범위가 올바르지 않습니다."),
    INVALID_INTERVAL(400, "범위 대비 버킷 수가 너무 많습니다."),
    INVALID_CURSOR(400, "손상되거나 만료된 커서입니다."),
    INVALID_SERVICE(400, "알 수 없는 서비스명입니다."),
    INVALID_FORMAT(400, "지원하지 않는 파일 형식입니다."),
    UNAUTHORIZED(401, "인증 토큰이 없거나 만료되었습니다."),
    FORBIDDEN(403, "관리자 권한이 없습니다."),
    ROUND_NOT_FOUND(404, "존재하지 않는 접수 회차입니다."),
    SESSION_NOT_FOUND(404, "만료되거나 발급받지 않은 세션입니다."),
    PAYLOAD_TOO_LARGE(413, "본문 크기가 너무 큽니다."),
    TOO_MANY_REQUESTS(429, "요청이 너무 많습니다."),
    TOO_MANY_CONNECTIONS(429, "동시 커넥션이 너무 많습니다."),
    METRIC_UNAVAILABLE(503, "아직 측정된 지표가 없습니다."),
    REPORT_GENERATION_FAILED(500, "리포트 생성에 실패했습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
}
