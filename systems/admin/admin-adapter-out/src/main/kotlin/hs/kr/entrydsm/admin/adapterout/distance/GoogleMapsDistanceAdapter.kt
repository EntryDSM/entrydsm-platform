package hs.kr.entrydsm.admin.adapterout.distance

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.DistancePort
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleMapsDistanceAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${admin.maps.api-key}") private val apiKey: String,
    @Value("\${admin.maps.base-url}") private val baseUrl: String,
    @Value("\${admin.maps.school-address}") private val schoolAddress: String,
    @Value("\${admin.maps.timeout-ms}") private val timeoutMs: Long,
) : DistancePort {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()

    override fun distanceFromSchool(address: String): Long =
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl(address)))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) unavailable()
            parseDistance(response.body())
        } catch (_: Exception) {
            unavailable()
        }

    fun parseDistance(body: String): Long {
        val root = objectMapper.readTree(body)
        if (root.path("status").asText() != "OK") unavailable()
        val element = root.path("rows").path(0).path("elements").path(0)
        if (element.path("status").asText() != "OK" || !element.path("distance").path("value").canConvertToLong()) {
            unavailable()
        }
        return element.path("distance").path("value").asLong()
    }

    private fun requestUrl(address: String): String =
        "$baseUrl?origins=${encode(address)}&destinations=${encode(schoolAddress)}&key=${encode(apiKey)}"

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun unavailable(): Nothing = throw AdminDomainException(ErrorCode.DISTANCE_SERVICE_UNAVAILABLE)
}
