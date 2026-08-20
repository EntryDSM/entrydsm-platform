package hs.kr.entrydsm.identity.config.security

import com.fasterxml.jackson.databind.ObjectMapper
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import java.nio.charset.StandardCharsets

class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = StandardCharsets.UTF_8.name()
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    error = ErrorDetail.from(ErrorCode.AUTH_UNAUTHORIZED)
                )
            )
        )
    }
}
