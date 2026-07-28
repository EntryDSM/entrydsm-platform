package hs.kr.entrydsm.gateway.application

data class GatewayAccessPolicy(
    val publicPaths: Set<String> = setOf("/actuator/health", "/actuator/info"),
) {
    fun isPublic(path: String): Boolean = publicPaths.any { path == it || path.startsWith("$it/") }
}
