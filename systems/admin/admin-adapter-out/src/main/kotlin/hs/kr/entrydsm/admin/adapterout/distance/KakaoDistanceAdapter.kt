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
class KakaoDistanceAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${admin.maps.api-key}") private val apiKey: String,
    @Value("\${admin.maps.geocoding-url}") private val geocodingUrl: String,
    @Value("\${admin.maps.directions-url}") private val directionsUrl: String,
    @Value("\${admin.maps.school-address}") private val schoolAddress: String,
    @Value("\${admin.maps.timeout-ms}") private val timeoutMs: Long,
) : DistancePort {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()
    private val schoolCoordinates by lazy { geocode(schoolAddress) }

    override fun distanceFromSchool(address: String): Long =
        try {
            val origin = geocode(address)
            val destination = schoolCoordinates
            val response = send(
                "$directionsUrl?origin=${origin.x},${origin.y}&destination=${destination.x},${destination.y}&summary=true",
            )
            parseDistance(response)
        } catch (_: Exception) {
            unavailable()
        }

    fun parseDistance(body: String): Long {
        val route = objectMapper.readTree(body).path("routes").path(0)
        if (route.path("result_code").asInt(-1) != 0 || !route.path("summary").path("distance").canConvertToLong()) {
            unavailable()
        }
        return route.path("summary").path("distance").asLong()
    }

    fun parseCoordinates(body: String): Coordinates {
        val document = objectMapper.readTree(body).path("documents").path(0)
        val x = document.path("x").asText().toDoubleOrNull() ?: unavailable()
        val y = document.path("y").asText().toDoubleOrNull() ?: unavailable()
        return Coordinates(x, y)
    }

    private fun geocode(address: String): Coordinates =
        parseCoordinates(send("$geocodingUrl?query=${encode(address)}"))

    private fun send(url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Authorization", "KakaoAK $apiKey")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) unavailable()
        return response.body()
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun unavailable(): Nothing = throw AdminDomainException(ErrorCode.DISTANCE_SERVICE_UNAVAILABLE)

    data class Coordinates(val x: Double, val y: Double)
}
