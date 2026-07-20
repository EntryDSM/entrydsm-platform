package hs.kr.entrydsm.identity.config.security

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import java.nio.charset.StandardCharsets

class JwtAuthorizationDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    error = ErrorDetail(
                        code = "ACCESS_DENIED",
                        message = "접근 권한이 없습니다.",
                        status = HttpServletResponse.SC_FORBIDDEN,
                    )
                )
            )
        )
    }
}
