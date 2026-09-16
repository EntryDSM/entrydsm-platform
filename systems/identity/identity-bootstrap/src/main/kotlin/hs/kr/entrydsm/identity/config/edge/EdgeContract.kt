package hs.kr.entrydsm.identity.config.edge

import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths

/**
 * 프로세스 바깥 경계에서 쓰는 이름과 규칙이다. gateway 가 갖고 있던 값을 그대로 옮겼다.
 */
object EdgeContract {
    /** 게이트웨이가 각 서비스로 넘기던 신뢰 헤더. 클라이언트가 보낸 값은 무조건 지우고 엣지가 다시 채운다. */
    const val USER_ID_HEADER = "X-User-Id"
    const val USER_ROLE_HEADER = "X-User-Role"
    const val LEGACY_USER_ID_HEADER = "user-id"
    const val SENSITIVE_AGREE_HEADER = "X-Sensitive-Agree"
    const val CLIENT_IP_HEADER = "X-Real-IP"
    const val FORWARDED_FOR_HEADER = "X-Forwarded-For"

    val UNTRUSTED_HEADERS = listOf(
        USER_ID_HEADER,
        USER_ROLE_HEADER,
        LEGACY_USER_ID_HEADER,
        SENSITIVE_AGREE_HEADER,
        CLIENT_IP_HEADER,
        FORWARDED_FOR_HEADER,
    )

    const val ACCESS_TOKEN_COOKIE = "access_token"

    /** identity 경로는 자기가 인증한다. 나머지 경로만 엣지가 쿠키로 인증해 신뢰 헤더를 채운다. */
    const val IDENTITY_PREFIX = "/api/identity"

    /** 게이트웨이 라우트가 받던 경로. 그 밖의 경로는 404 로 끊는다. */
    val ROUTED_PREFIXES = listOf(
        IDENTITY_PREFIX,
        "/api/application",
        "/api/evaluation",
        "/api/v11/admin",
        "/api/notification",
        "/api/monitor",
        "/api/document",
        "/api/schedule",
    )

    /** 엣지가 직접 처리하거나 상태 점검에 쓰는 경로. */
    val EXPOSED_PATHS = listOf("/actuator/health", "/actuator/info")

    val PUBLIC_PATHS = AuthEndpointPaths.PUBLIC + AuthEndpointPaths.LOGOUT

    fun isRouted(path: String): Boolean =
        ROUTED_PREFIXES.any { path == it || path.startsWith("$it/") } ||
            EXPOSED_PATHS.any { path == it || path.startsWith("$it/") }

    fun isIdentityPath(path: String): Boolean =
        path == IDENTITY_PREFIX || path.startsWith("$IDENTITY_PREFIX/")
}

/** 엣지가 돌려주는 오류 코드. gateway JSON 응답의 `error` 값과 같다. */
enum class EdgeError(val status: Int) {
    INVALID_TRACE_ID(400),
    AUTH_UNAUTHORIZED(401),
    ROUTE_NOT_FOUND(404),
    METHOD_NOT_ALLOWED(405),
    UNSUPPORTED_MEDIA_TYPE(415),
    REQUEST_TOO_LARGE(413),
    IDENTITY_UNAVAILABLE(503),
}
