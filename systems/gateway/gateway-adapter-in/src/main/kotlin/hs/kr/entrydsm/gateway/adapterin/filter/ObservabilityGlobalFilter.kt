package hs.kr.entrydsm.gateway.adapterin.filter

import hs.kr.entrydsm.gateway.domain.GatewayService
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
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

/** 게이트웨이를 통과한 외부 API 요청을 한 번만 집계한다. */
@Component
class ObservabilityGlobalFilter(
    private val redis: StringRedisTemplate,
) : GlobalFilter, Ordered {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (!path.startsWith("/api/") || path.startsWith(GatewayService.OBSERVABILITY.pathPrefix)) {
            return chain.filter(exchange)
        }
        return chain.filter(exchange)
            .then(Mono.defer { record(exchange, exchange.response.statusCode?.value() ?: 200) })
            .onErrorResume { failure -> record(exchange, exchange.response.statusCode?.value() ?: 500).then(Mono.error(failure)) }
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE + 1

    private fun record(exchange: ServerWebExchange, status: Int): Mono<Void> = Mono.fromRunnable<Void> {
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
    }.subscribeOn(Schedulers.boundedElastic()).then()

    private fun increment(key: String) {
        redis.opsForValue().increment(key)
        redis.expire(key, RETENTION)
    }

    private fun recordServerError(exchange: ServerWebExchange, status: Int, now: Instant) {
        val service = observedService(exchange.request.path.value()) ?: return
        val method = exchange.request.method.name()
        val path = normalizePath(exchange.request.path.value())
        val code = "HTTP_$status"
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
                "message" to (exchange.response.statusCode?.toString() ?: "Request failed"),
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

private fun normalizePath(path: String): String = path.replace(Regex("/\\d+(?=/|$)"), "/{id}")
