package hs.kr.entrydsm.identity.config.security

import jakarta.servlet.http.HttpServletResponse
import java.time.Instant

object RedisUnavailableResponseWriter {
    fun write(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"success":false,"error":{"code":"REDIS_UNAVAILABLE","message":"인증 상태 저장소를 사용할 수 없습니다.","status":503},"timestamp":"${Instant.now()}"}""",
        )
    }
}
