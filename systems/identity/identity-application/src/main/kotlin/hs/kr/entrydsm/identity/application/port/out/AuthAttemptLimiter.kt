package hs.kr.entrydsm.identity.application.port.out

/** 로그인과 비밀번호 재설정의 시도 횟수를 각각 제한한다. */
interface AuthAttemptLimiter {
    fun checkLogin(loginId: String)
    fun checkPasswordReset(loginId: String)
}
