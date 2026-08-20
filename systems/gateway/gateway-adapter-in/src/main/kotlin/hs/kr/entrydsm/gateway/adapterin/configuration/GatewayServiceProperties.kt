package hs.kr.entrydsm.gateway.adapterin.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import hs.kr.entrydsm.gateway.domain.GatewayService
import java.net.URI

@ConfigurationProperties(prefix = "gateway.services")
data class GatewayServiceProperties(
    var identity: URI = URI("http://localhost:8081"),
    var application: URI = URI("http://localhost:8082"),
    var admin: URI = URI("http://localhost:8083"),
    var notification: URI = URI("http://localhost:8084"),
    var observability: URI = URI("http://localhost:8085"),
    var configuration: URI = URI("http://localhost:8086"),
) {
    init {
        serviceUris.forEach(::validate)
    }

    @jakarta.annotation.PostConstruct
    fun validateAfterBinding() {
        serviceUris.forEach(::validate)
    }

    val serviceUris: Map<GatewayService, URI>
        get() = mapOf(
            GatewayService.IDENTITY to identity,
            GatewayService.APPLICATION to application,
            GatewayService.ADMIN to admin,
            GatewayService.NOTIFICATION to notification,
            GatewayService.OBSERVABILITY to observability,
            GatewayService.CONFIGURATION to configuration,
        )

    private fun validate(entry: Map.Entry<GatewayService, URI>) {
        val serviceName = entry.key.routeId
        val uri = entry.value
        require(uri.scheme in SUPPORTED_SCHEMES && !uri.host.isNullOrBlank() && !uri.isOpaque) {
            "gateway.services.$serviceName must be an absolute HTTP(S) URI without path, query or fragment: $uri"
        }
        require(uri.userInfo == null && uri.path.isNullOrEmpty() && uri.query == null && uri.fragment == null) {
            "gateway.services.$serviceName must not contain user info, path, query or fragment: $uri"
        }
    }

    private companion object {
        val SUPPORTED_SCHEMES = setOf("http", "https")
    }
}
