package hs.kr.entrydsm.observability.adapterin.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * rate limit의 기준이 되는 클라이언트 IP를 정한다.
 * 게이트웨이는 X-Forwarded-For를 지우고, 직접 판정한 IP를 X-Real-IP로 넘긴다(클라이언트가 보낸 X-Real-IP는 게이트웨이가 지운다).
 * X-User-Id처럼 게이트웨이 헤더를 믿으므로 이 서비스는 게이트웨이 뒤에만 노출되어야 한다.
 * 게이트웨이를 거치지 않은 요청(로컬 개발)은 remoteAddr을 쓴다.
 */
@Component
class ClientIpResolver {

    fun resolve(request: HttpServletRequest): String = resolve(request.remoteAddr, request.getHeader(REAL_IP))

    fun resolve(remoteAddr: String?, realIp: String?): String =
        realIp?.trim()?.takeIf { it.isNotEmpty() }
            ?: remoteAddr?.takeIf { it.isNotBlank() }
            ?: UNKNOWN

    companion object {
        private const val REAL_IP = "X-Real-IP"
        private const val UNKNOWN = "unknown"
    }
}
