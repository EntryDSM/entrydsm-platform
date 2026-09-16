package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 요청마다 trace id 를 정해 응답 헤더와 로그 MDC 에 남긴다. gateway 의 TraceIdGlobalFilter 를 옮겼다.
 *
 * 클라이언트가 보낸 값은 형식을 검사해서 통과한 것만 쓴다. 형식이 틀리면 값을 되돌려주지 않고 400 으로 끊는다.
 */
class TraceIdFilter(
    private val generator: () -> String = { UUID.randomUUID().toString() },
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(TraceId.HEADER_NAME)
        val traceId = try {
            if (header == null) TraceId.generated(generator) else TraceId.from(header)
        } catch (exception: IllegalArgumentException) {
            EdgeErrorResponseWriter.write(request, response, EdgeError.INVALID_TRACE_ID)
            return
        }

        request.setAttribute(TraceId.HEADER_NAME, traceId.value)
        response.setHeader(TraceId.HEADER_NAME, traceId.value)
        MDC.put(TraceId.HEADER_NAME, traceId.value)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TraceId.HEADER_NAME)
        }
    }
}
