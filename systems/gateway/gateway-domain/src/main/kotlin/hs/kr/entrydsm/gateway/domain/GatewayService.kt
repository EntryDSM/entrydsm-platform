package hs.kr.entrydsm.gateway.domain

enum class GatewayDownstream(
    val propertyKey: String,
) {
    IDENTITY("identity"),
    APPLICATION("application"),
    ADMIN("admin"),
    NOTIFICATION("notification"),
    OBSERVABILITY("observability"),
    CONFIGURATION("configuration"),
}

enum class GatewayService(
    val routeId: String,
    val downstreamKey: GatewayDownstream,
    val pathPrefix: String,
) {
    IDENTITY("identity", GatewayDownstream.IDENTITY, "/api/identity"),
    APPLICATION("application", GatewayDownstream.APPLICATION, "/api/application"),
    EVALUATION("evaluation", GatewayDownstream.APPLICATION, "/api/evaluation"),
    ADMIN("admin", GatewayDownstream.ADMIN, "/api/v11/admin"),
    NOTIFICATION("notification", GatewayDownstream.NOTIFICATION, "/api/notification"),
    OBSERVABILITY("observability", GatewayDownstream.OBSERVABILITY, "/api/monitor"),
    CONFIGURATION("configuration", GatewayDownstream.CONFIGURATION, "/api/document"),
    SCHEDULE("schedule", GatewayDownstream.CONFIGURATION, "/api/schedule"),
    ;

    companion object {
        fun validateDefinitions() {
            requireUnique("route ID", entries.map(GatewayService::routeId))
            requireUnique("path prefix", entries.map(GatewayService::pathPrefix))
            requireUnique("downstream property key", GatewayDownstream.entries.map(GatewayDownstream::propertyKey))

            entries.forEach { service ->
                require(service.routeId.isNotBlank()) { "Gateway route ID must not be blank" }
                require(service.pathPrefix.startsWith("/api/") && !service.pathPrefix.endsWith("/")) {
                    "Gateway path prefix must start with /api/ and must not end with '/': ${service.pathPrefix}"
                }
            }
        }

        private fun requireUnique(label: String, values: List<String>) {
            val duplicates = values.groupingBy { it }.eachCount().filterValues { count -> count > 1 }.keys
            require(duplicates.isEmpty()) { "Gateway $label values must be unique: $duplicates" }
        }
    }
}
