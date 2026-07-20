package hs.kr.entrydsm.identity.domain.enum

/**
 * Identity API에서 클라이언트에 노출하는 오류 코드입니다.
 *
 * @property status 오류에 대응하는 HTTP 상태 코드
 * @property message 클라이언트에 전달할 기본 오류 메시지
 */
enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    INVALID_REQUEST_BODY(400, "요청 본문이 올바르지 않습니다."),
    AUTH_UNAUTHORIZED(401, "인증이 필요합니다."),
    INVALID_CREDENTIALS(401, "로그인 식별자 또는 비밀번호가 일치하지 않습니다."),
    INVALID_REFRESH_TOKEN(401, "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(401, "만료된 리프레시 토큰입니다."),
    ACCOUNT_INACTIVE(403, "비활성화된 계정입니다."),
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
    ACCOUNT_ALREADY_EXISTS(409, "이미 가입된 로그인 식별자입니다."),
    PASSWORD_SAME_AS_OLD(409, "기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다."),
    ACCOUNT_DELETE_NOT_ALLOWED(409, "제출된 원서가 있어 회원탈퇴를 할 수 없습니다."),
    APPLICATION_RESULT_NOT_AVAILABLE(409, "아직 합격 여부를 조회할 수 없습니다."),
    APPLICATION_CANCEL_NOT_ALLOWED(409, "현재 상태에서는 원서 제출을 취소할 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
}
