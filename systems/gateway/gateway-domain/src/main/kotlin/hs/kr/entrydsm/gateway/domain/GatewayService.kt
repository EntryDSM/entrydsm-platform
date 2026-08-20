package hs.kr.entrydsm.gateway.domain

enum class GatewayService(
    val routeId: String,
    val pathPrefix: String,
) {
    IDENTITY("identity", "/api/identity"),
    APPLICATION("application", "/api/application"),
    ADMIN("admin", "/api/admin"),
    NOTIFICATION("notification", "/api/notification"),
    OBSERVABILITY("observability", "/api/observability"),
    CONFIGURATION("configuration", "/api/configuration"),
}
