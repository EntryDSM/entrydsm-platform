package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.nio.charset.StandardCharsets

/**
 * 엣지 단계 오류를 gateway 와 같은 형태로 쓴다: `{"status":401,"error":"AUTH_UNAUTHORIZED","traceId":"..."}`.
 *
 * 모듈 컨트롤러까지 가지 못한 요청이라 모듈별 오류 형식을 쓸 수 없다.
 */
object EdgeErrorResponseWriter {
    fun write(request: HttpServletRequest, response: HttpServletResponse, error: EdgeError) {
        if (response.isCommitted) return
        val traceId = response.getHeader(TraceId.HEADER_NAME)
            ?: request.getAttribute(TraceId.HEADER_NAME) as? String
            ?: ""
        response.status = error.status
        response.contentType = "application/json"
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.setHeader("Cache-Control", "no-store")
        response.writer.write(body(error, traceId))
        response.writer.flush()
    }

    fun body(error: EdgeError, traceId: String): String =
        """{"status":${error.status},"error":"${error.name}","traceId":"${escape(traceId)}"}"""

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
