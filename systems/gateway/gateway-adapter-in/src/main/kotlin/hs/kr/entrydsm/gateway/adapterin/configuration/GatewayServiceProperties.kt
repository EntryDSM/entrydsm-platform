package hs.kr.entrydsm.gateway.adapterin.configuration

import hs.kr.entrydsm.gateway.domain.GatewayDownstream
import hs.kr.entrydsm.gateway.domain.GatewayService
import org.springframework.boot.context.properties.ConfigurationProperties
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
        validate()
    }

    @jakarta.annotation.PostConstruct
    fun validateAfterBinding() {
        validate()
    }

    val serviceUris: Map<GatewayService, URI>
        get() = GatewayService.entries.associateWith { service -> downstreamUris.getValue(service.downstreamKey) }

    private val downstreamUris: Map<GatewayDownstream, URI>
        get() = mapOf(
            GatewayDownstream.IDENTITY to identity,
            GatewayDownstream.APPLICATION to application,
            GatewayDownstream.ADMIN to admin,
            GatewayDownstream.NOTIFICATION to notification,
            GatewayDownstream.OBSERVABILITY to observability,
            GatewayDownstream.CONFIGURATION to configuration,
        )

    private fun validate() {
        GatewayService.validateDefinitions()
        val uris = downstreamUris
        require(uris.keys == GatewayDownstream.entries.toSet()) {
            "Every gateway downstream must have exactly one configured URI"
        }
        uris.forEach(::validate)
    }

    private fun validate(entry: Map.Entry<GatewayDownstream, URI>) {
        val serviceName = entry.key.propertyKey
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
