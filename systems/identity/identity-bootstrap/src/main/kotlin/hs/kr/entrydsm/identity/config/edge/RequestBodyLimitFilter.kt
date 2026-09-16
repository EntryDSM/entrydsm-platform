package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/** 본문이 상한을 넘으면 처리하지 않는다. gateway 의 RequestSizeGlobalFilter 를 옮겼다. */
class RequestBodyLimitFilter(
    private val maxBodyBytes: Long,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.contentLengthLong > maxBodyBytes) {
            EdgeErrorResponseWriter.write(request, response, EdgeError.REQUEST_TOO_LARGE)
            return
        }

        try {
            filterChain.doFilter(CountingRequestWrapper(request, maxBodyBytes), response)
        } catch (exception: RequestTooLargeException) {
            EdgeErrorResponseWriter.write(request, response, EdgeError.REQUEST_TOO_LARGE)
        }
    }
}

class RequestTooLargeException : RuntimeException("request body exceeds the configured limit")

/** Content-Length 를 주지 않는 요청(chunked)도 읽은 바이트로 상한을 건다. */
private class CountingRequestWrapper(
    request: HttpServletRequest,
    private val maxBodyBytes: Long,
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream {
        val delegate = super.getInputStream()
        return object : ServletInputStream() {
            private var counted = 0L

            override fun read(): Int = delegate.read().also { if (it >= 0) count(1) }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                delegate.read(buffer, offset, length).also { if (it > 0) count(it.toLong()) }

            override fun isFinished(): Boolean = delegate.isFinished

            override fun isReady(): Boolean = delegate.isReady

            override fun setReadListener(listener: ReadListener) = delegate.setReadListener(listener)

            private fun count(bytes: Long) {
                counted += bytes
                if (counted > maxBodyBytes) throw RequestTooLargeException()
            }
        }
    }
}
