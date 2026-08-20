package hs.kr.entrydsm.observability.adapterin.web

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.observability.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.observability.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 수집 엔드포인트의 본문 크기를 역직렬화 전에 제한한다.
 * 컨트롤러에서 확인하면 이미 파싱이 끝난 뒤라 큰 본문이 메모리와 CPU를 먼저 쓴다.
 * Content-Length가 없는 chunked 요청은 크기를 미리 알 수 없으므로 함께 거부한다(수집 클라이언트는 항상 길이를 붙인다).
 */
class PayloadSizeLimitFilter(
    private val maxBytes: Long,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val contentLength = request.contentLengthLong
        if (contentLength > maxBytes || contentLength < 0) {
            response.status = ErrorCode.PAYLOAD_TOO_LARGE.status
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(response.writer, ErrorResponse(error = ErrorDetail.from(ErrorCode.PAYLOAD_TOO_LARGE)))
            return
        }
        filterChain.doFilter(request, response)
    }
}

@Configuration(proxyBeanMethods = false)
class PayloadSizeLimitConfig {

    @Bean
    fun payloadSizeLimitFilter(objectMapper: ObjectMapper): FilterRegistrationBean<PayloadSizeLimitFilter> =
        FilterRegistrationBean(PayloadSizeLimitFilter(MAX_COLLECT_PAYLOAD_BYTES, objectMapper)).apply {
            addUrlPatterns("/api/monitor/v11/collect/*")
        }

    companion object {
        private const val MAX_COLLECT_PAYLOAD_BYTES = 64L * 1024
    }
}
