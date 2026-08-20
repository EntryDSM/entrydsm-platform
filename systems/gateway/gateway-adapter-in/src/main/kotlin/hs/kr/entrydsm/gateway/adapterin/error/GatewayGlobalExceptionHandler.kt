package hs.kr.entrydsm.gateway.adapterin.error

import hs.kr.entrydsm.gateway.application.DownstreamFailurePolicy
import hs.kr.entrydsm.gateway.application.DownstreamFailureType
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayRequestTooLargeException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GatewayGlobalExceptionHandler(
    private val responseWriter: GatewayErrorResponseWriter,
) : WebExceptionHandler, Ordered {
    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        if (exchange.response.isCommitted) {
            return Mono.error(ex)
        }

        val response = classify(ex)
        return responseWriter.write(exchange, response.status, response.error)
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    private fun classify(error: Throwable): GatewayError {
        when (DownstreamFailurePolicy.classify(error)) {
            DownstreamFailureType.TIMEOUT -> return GatewayError(
                HttpStatus.GATEWAY_TIMEOUT,
                "GATEWAY_TIMEOUT",
            )

            DownstreamFailureType.CONNECTION -> return GatewayError(
                HttpStatus.BAD_GATEWAY,
                "BAD_GATEWAY",
            )

            null -> Unit
        }

        val status = findResponseStatus(error)
        if (status != null) {
            return GatewayError(status, statusError(status))
        }
        if (error is GatewayRequestTooLargeException) {
            return GatewayError(HttpStatusCode.valueOf(413), "REQUEST_TOO_LARGE")
        }
        if (error is InvalidTraceIdException) {
            return GatewayError(HttpStatus.BAD_REQUEST, "INVALID_TRACE_ID")
        }
        return GatewayError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_GATEWAY_ERROR")
    }

    private fun findResponseStatus(error: Throwable): HttpStatusCode? {
        var current: Throwable? = error
        while (current != null) {
            if (current is ResponseStatusException) {
                return current.statusCode
            }
            current = current.cause
        }
        return null
    }

    private fun statusError(status: HttpStatusCode): String = when (status.value()) {
        400 -> "BAD_REQUEST"
        404 -> "ROUTE_NOT_FOUND"
        413 -> "REQUEST_TOO_LARGE"
        else -> "GATEWAY_ERROR"
    }

    private data class GatewayError(
        val status: HttpStatusCode,
        val error: String,
    )
}
