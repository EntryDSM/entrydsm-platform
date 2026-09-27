package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.domain.GatewayService
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import tools.jackson.databind.ObjectMapper

/** 게이트웨이를 통과한 외부 API 요청을 한 번만 집계한다. */
@Component
class ObservabilityGlobalFilter(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : GlobalFilter, Ordered {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith("/api/") || path.startsWith(GatewayService.OBSERVABILITY.pathPrefix)) {
            return chain.filter(exchange)
        }
        return chain.filter(exchange)
            .doOnSuccess { record(exchange, exchange.response.statusCode?.value() ?: 200) }
            .doOnError { record(exchange, exchange.response.statusCode?.value() ?: 500) }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1

    /**
     * 기록은 요청 흐름 밖에서 돈다. 요청 흐름에 묶으면 응답 직후 연결이 닫힐 때 구독과 함께 취소되고
     * 스레드가 인터럽트돼 Redis 쓰기가 빠진다(요청마다 연결을 닫으면 재시작 뒤 아무것도 남지 않았다).
     */
    private fun record(exchange: ServerWebExchange, status: Int) {
        Schedulers.boundedElastic().schedule { recordNow(exchange, status) }
    }

    private fun recordNow(exchange: ServerWebExchange, status: Int) {
        runCatching {
            val now = Instant.now()
            val bucket = now.toEpochMilli() / GRANULARITY.toMillis() * GRANULARITY.toMillis()
            val success = status < 400
            increment("monitor:metric:api:${result(success)}:$bucket")
            businessMetric(exchange.request.method, exchange.request.path.value())?.let { type ->
                increment("monitor:metric:business:$type:${result(success)}:$bucket")
            }
            if (!success) recordServerError(exchange, status, now)
        }.onFailure { logger.warn("Failed to record gateway observability metrics", it) }
    }

    private fun increment(key: String) {
        redis.opsForValue().increment(key)
        redis.expire(key, RETENTION)
    }

    private fun recordServerError(exchange: ServerWebExchange, status: Int, now: Instant) {
        val service = observedService(exchange.request.path.value()) ?: return
        val method = exchange.request.method.name()
        val path = normalizePath(exchange.request.path.value())
        val code = "HTTP_$status"
        val message = exchange.response.statusCode?.toString() ?: "Request failed"
        val fingerprint = fingerprint(service, method, path, code)
        val entryKey = "monitor:server-log:entry:$fingerprint"
        val hash = redis.opsForHash<String, String>()
        val count = hash.increment(entryKey, "count", 1)
        val nowMillis = now.toEpochMilli().toString()
        if (count == 1L) {
            hash.putAll(entryKey, mapOf(
                "service" to service,
                "method" to method,
                "path" to path,
                "status" to status.toString(),
                "code" to code,
                "message" to message,
                "firstOccurredAt" to nowMillis,
                "lastOccurredAt" to nowMillis,
            ))
        } else {
            hash.put(entryKey, "lastOccurredAt", nowMillis)
        }
        redis.expire(entryKey, SERVER_LOG_RETENTION)
        val groups = listOf("ALL", "service:$service", "status:$status", "status:${status / 100}xx", "service:$service:status:$status", "service:$service:status:${status / 100}xx")
        val expiredBefore = now.minus(SERVER_LOG_RETENTION).toEpochMilli().toDouble()
        groups.forEach {
            val indexKey = "monitor:server-log:index:$it"
            redis.opsForZSet().add(indexKey, fingerprint, now.toEpochMilli().toDouble())
            redis.opsForZSet().removeRangeByScore(indexKey, 0.0, expiredBefore)
        }
        liveServerError(service, method, path, status, code, message, count, now)?.let {
            redis.convertAndSend(LIVE_LOG_CHANNEL, objectMapper.writeValueAsString(it))
        }
    }

    private fun result(success: Boolean) = if (success) "success" else "failure"

    private fun fingerprint(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
        .digest(parts.joinToString("|").toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

    private companion object {
        val GRANULARITY: Duration = Duration.ofMinutes(5)
        val RETENTION: Duration = Duration.ofDays(91)
        val SERVER_LOG_RETENTION: Duration = Duration.ofDays(7)

        /** observability 가 구독해 SSE log 이벤트로 내보내는 채널(SseLiveLogPublisher.CHANNEL). */
        const val LIVE_LOG_CHANNEL = "monitor:live-log"
    }
}

fun businessMetric(method: HttpMethod, path: String): String? = when {
    method == HttpMethod.PATCH && path == "/api/application/v11/applicants" -> "application-submit"
    method == HttpMethod.GET && (path == "/api/document/v11/applications" || path.startsWith("/api/document/v11/applications/") || path.startsWith("/api/document/v11/admission-tickets/")) -> "pdf-download"
    else -> null
}

fun observedService(path: String): String? = GatewayService.entries.firstOrNull { path.startsWith(it.pathPrefix) }?.let {
    when (it) {
        GatewayService.IDENTITY -> "IDENTITY"
        GatewayService.APPLICATION -> "APPLICATION"
        GatewayService.EVALUATION -> "EVALUATION"
        GatewayService.NOTIFICATION -> "NOTIFICATION"
        GatewayService.CONFIGURATION -> "DOCUMENT"
        GatewayService.SCHEDULE -> "SCHEDULE"
        else -> null
    }
}

/**
 * 모니터링 화면이 SSE log 이벤트(kind SERVER)로 받는 실시간 서버 오류.
 * 화면은 5xx 만 보여 주고 실시간 로그 버퍼를 클라이언트 로그와 같이 써서, 4xx 는 목록 API 로만 본다.
 */
fun liveServerError(
    service: String,
    method: String,
    path: String,
    status: Int,
    code: String,
    message: String,
    count: Long,
    at: Instant,
): Map<String, Any>? = if (status < 500) null else mapOf(
    "kind" to "SERVER",
    "level" to "ERROR",
    "service" to service,
    "method" to method,
    "path" to path,
    "status" to status,
    "code" to code,
    "message" to message,
    "count" to count,
    // 모니터링 응답 시각과 같이 한국 시간으로 보낸다(#290).
    "occurredAt" to DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.atZone(KOREA)),
)

private val KOREA: ZoneId = ZoneId.of("Asia/Seoul")

private fun normalizePath(path: String): String = path.replace(Regex("/\\d+(?=/|$)"), "/{id}")
