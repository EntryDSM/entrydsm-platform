package hs.kr.entrydsm.identity.application.port.out

/** 로그인 실패와 비밀번호 재설정 시도를 각각 제한한다. */
interface AuthAttemptLimiter {
    /** 로그인 전에 기존 차단 여부만 확인한다. */
    fun checkLogin(loginId: String, clientIp: String)
    /** 자격 증명 불일치만 실패 횟수에 반영한다. */
    fun recordLoginFailure(loginId: String, clientIp: String)
    /** 차단 여부를 재확인하고 해당 IP와 계정의 연속 실패를 초기화한다. */
    fun recordLoginSuccess(loginId: String, clientIp: String)
    fun checkPasswordReset(loginId: String)
}
