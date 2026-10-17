package hs.kr.entrydsm.application.administration.adapterout.distance

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.out.DistancePort
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class KakaoDistanceAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${admin.maps.api-key}") private val apiKey: String,
    @Value("\${admin.maps.geocoding-url}") private val geocodingUrl: String,
    @Value("\${admin.maps.directions-url}") private val directionsUrl: String,
    @Value("\${admin.maps.school-address}") private val schoolAddress: String,
    @Value("\${admin.maps.timeout-ms}") private val timeoutMs: Long,
) : DistancePort {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()
    private val schoolCoordinates by lazy { geocode(schoolAddress, "SCHOOL_GEOCODING") }

    // 외부 응답과 예외 메시지 대신 단계와 안전한 원인만 남긴다.
    override fun distanceFromSchool(address: String): Long {
        var stage = "APPLICANT_GEOCODING"
        return try {
            val origin = geocode(address, stage)
            stage = "SCHOOL_GEOCODING"
            val destination = schoolCoordinates
            stage = "DIRECTIONS"
            val response = send(
                "$directionsUrl?origin=${origin.x},${origin.y}&destination=${destination.x},${destination.y}&summary=true",
                stage,
            )
            parseDistance(response)
        } catch (exception: AdminDomainException) {
            throw exception
        } catch (exception: Exception) {
            val reason = when (exception) {
                is JacksonException -> "JSON_PARSING"
                is HttpTimeoutException -> "TIMEOUT"
                is IOException -> "NETWORK"
                else -> "UNEXPECTED"
            }
            unavailable(stage, reason, exceptionClass = exception.javaClass.name)
        }
    }

    // 카카오의 경로 결과 코드와 거리 필드 실패를 구분한다.
    fun parseDistance(body: String): Long {
        val route = objectMapper.readTree(body).path("routes").path(0)
        val resultCode = route.path("result_code").asInt(-1)
        if (resultCode != 0) {
            unavailable("DIRECTIONS", "ROUTE_RESULT", resultCode = resultCode)
        }
        if (!route.path("summary").path("distance").canConvertToLong()) {
            unavailable("DIRECTIONS", "DISTANCE_MISSING")
        }
        return route.path("summary").path("distance").asLong()
    }

    // 기존 좌표 파싱 호출 인터페이스를 유지한다.
    fun parseCoordinates(body: String): Coordinates = parseCoordinates(body, "APPLICANT_GEOCODING")

    // 검색 결과 없음과 좌표 형식 오류를 구분한다.
    private fun parseCoordinates(body: String, stage: String): Coordinates {
        val documents = objectMapper.readTree(body).path("documents")
        if (documents.isArray && documents.isEmpty) unavailable(stage, "EMPTY_SEARCH")
        val document = documents.path(0)
        val x = document.path("x").asText().toDoubleOrNull() ?: unavailable(stage, "COORDINATE_FORMAT")
        val y = document.path("y").asText().toDoubleOrNull() ?: unavailable(stage, "COORDINATE_FORMAT")
        return Coordinates(x, y)
    }

    // 주소는 요청에만 사용하며 로그에 남기지 않는다.
    private fun geocode(address: String, stage: String): Coordinates =
        parseCoordinates(send("$geocodingUrl?query=${encode(address)}", stage), stage)

    // HTTP 실패 시 본문과 URL을 제외한 상태 코드만 기록한다.
    private fun send(url: String, stage: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Authorization", "KakaoAK $apiKey")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) unavailable(stage, "HTTP_FAILURE", httpStatus = response.statusCode())
        return response.body()
    }

    // 기존 검색어 인코딩을 유지한다.
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    // 허용한 진단 필드만 기록하고 기존 외부 오류 계약을 유지한다.
    private fun unavailable(
        stage: String,
        reason: String,
        httpStatus: Int? = null,
        resultCode: Int? = null,
        exceptionClass: String? = null,
    ): Nothing {
        logger.error(
            "Kakao distance failure [stage={}, reason={}, httpStatus={}, resultCode={}, exceptionClass={}]",
            stage, reason, httpStatus, resultCode, exceptionClass,
        )
        throw AdminDomainException(ErrorCode.DISTANCE_SERVICE_UNAVAILABLE)
    }

    data class Coordinates(val x: Double, val y: Double)
}
