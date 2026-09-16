package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 어떤 모듈의 컨트롤러에도 닿지 못한 요청의 오류를 엣지 형식으로 돌려준다.
 *
 * 모듈 advice 는 자기 패키지 컨트롤러만 처리하므로 없는 하위 경로·허용하지 않는 메서드는 아무도 처리하지 않는다.
 * 순서를 가장 뒤로 두어 모듈 advice 가 먼저 처리하게 한다.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class EdgeFallbackExceptionHandler {
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(request: HttpServletRequest): ResponseEntity<String> =
        edgeError(request, EdgeError.ROUTE_NOT_FOUND)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(request: HttpServletRequest): ResponseEntity<String> =
        edgeError(request, EdgeError.METHOD_NOT_ALLOWED)

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(request: HttpServletRequest): ResponseEntity<String> =
        edgeError(request, EdgeError.UNSUPPORTED_MEDIA_TYPE)

    /** 멀티파트 상한은 컨트롤러를 고르기 전에 걸리므로 모듈 advice 가 받지 못한다. */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(request: HttpServletRequest): ResponseEntity<String> =
        edgeError(request, EdgeError.REQUEST_TOO_LARGE)

    private fun edgeError(request: HttpServletRequest, error: EdgeError): ResponseEntity<String> {
        val traceId = request.getAttribute(TraceId.HEADER_NAME) as? String ?: ""
        return ResponseEntity.status(error.status)
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(EdgeErrorResponseWriter.body(error, traceId))
    }
}
