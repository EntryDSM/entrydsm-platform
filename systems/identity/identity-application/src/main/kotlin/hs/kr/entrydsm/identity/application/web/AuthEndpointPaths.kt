package hs.kr.entrydsm.identity.application.web

object AuthEndpointPaths {
    const val BASE = "/api/identity/v11/auth"
    const val SIGNUP_PATH = "/signup"
    const val LOGIN_PATH = "/login"
    const val LOGOUT_PATH = "/logout"
    const val TOKEN_PATH = "/token"
    const val PASSWORD_RESET_PATH = "/password-reset"
    const val SIGNUP = "$BASE$SIGNUP_PATH"
    const val LOGIN = "$BASE$LOGIN_PATH"
    const val LOGOUT = "$BASE$LOGOUT_PATH"
    const val TOKEN = "$BASE$TOKEN_PATH"
    const val PASSWORD_RESET = "$BASE$PASSWORD_RESET_PATH"

    val PUBLIC = setOf(SIGNUP, LOGIN, TOKEN)
}
