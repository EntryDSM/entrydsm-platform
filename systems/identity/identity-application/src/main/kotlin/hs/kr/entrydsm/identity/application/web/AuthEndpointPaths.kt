package hs.kr.entrydsm.identity.application.web

object AuthEndpointPaths {
    const val BASE = "/api/identity/v11/auth"
    const val SIGNUP_PATH = "/signup"
    const val LOGIN_PATH = "/login"
    const val LOGOUT_PATH = "/logout"
    const val TOKEN_PATH = "/token"
    const val PASSWORD_RESET_PATH = "/password-reset"
    const val PASS_PATH = "/pass"
    const val PASS_POPUP_PATH = "/popup"
    const val PASS_INFO_PATH = "/info"
    const val SIGNUP = "$BASE$SIGNUP_PATH"
    const val LOGIN = "$BASE$LOGIN_PATH"
    const val LOGOUT = "$BASE$LOGOUT_PATH"
    const val TOKEN = "$BASE$TOKEN_PATH"
    const val PASSWORD_RESET = "$BASE$PASSWORD_RESET_PATH"
    const val PASS_POPUP = "$BASE$PASS_PATH$PASS_POPUP_PATH"
    const val PASS_INFO = "$BASE$PASS_PATH$PASS_INFO_PATH"

    val PUBLIC = setOf(SIGNUP, LOGIN, TOKEN, PASSWORD_RESET, PASS_POPUP, PASS_INFO)
}
