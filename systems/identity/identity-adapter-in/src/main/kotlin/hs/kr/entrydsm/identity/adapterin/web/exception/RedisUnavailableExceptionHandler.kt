package hs.kr.entrydsm.identity.adapterin.web.exception

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorDetail
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ErrorResponse
import hs.kr.entrydsm.identity.application.port.out.RefreshTokenStoreUnavailableException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class RedisUnavailableExceptionHandler {
    @ExceptionHandler(RefreshTokenStoreUnavailableException::class)
    fun handle(exception: RefreshTokenStoreUnavailableException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(
                ErrorResponse(
                    error = ErrorDetail(
                        code = "REDIS_UNAVAILABLE",
                        message = "인증 상태 저장소를 사용할 수 없습니다.",
                        status = HttpStatus.SERVICE_UNAVAILABLE.value(),
                    ),
                ),
            )
}
