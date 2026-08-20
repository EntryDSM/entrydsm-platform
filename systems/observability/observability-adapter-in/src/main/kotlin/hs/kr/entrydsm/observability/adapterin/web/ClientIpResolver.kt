package hs.kr.entrydsm.observability.adapterin.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * rate limit·커넥션 제한의 기준이 되는 클라이언트 IP를 정한다.
 * X-Forwarded-For는 누구나 붙일 수 있으므로 신뢰하는 프록시를 거쳐 들어온 요청에서만 사용한다.
 * monitor.trusted-proxies가 비어 있으면(기본값) 항상 remoteAddr을 쓴다.
 */
@Component
class ClientIpResolver(
    @Value("\${monitor.trusted-proxies:}") trustedProxies: String,
) {
    private val trusted: Set<String> = trustedProxies.split(",").map { it.trim() }.filterNot { it.isEmpty() }.toSet()

    fun resolve(request: HttpServletRequest): String = resolve(request.remoteAddr, request.getHeader(FORWARDED_FOR))

    fun resolve(remoteAddr: String?, forwardedFor: String?): String {
        val peer = remoteAddr?.takeIf { it.isNotBlank() } ?: UNKNOWN
        if (peer !in trusted) return peer
        // 오른쪽부터 신뢰 프록시를 걷어낸 첫 값이 실제 클라이언트다. 그 왼쪽은 클라이언트가 위조할 수 있다.
        val forwarded = forwardedFor?.split(",")?.map { it.trim() }?.filterNot { it.isEmpty() } ?: return peer
        return forwarded.lastOrNull { it !in trusted } ?: peer
    }

    companion object {
        private const val FORWARDED_FOR = "X-Forwarded-For"
        private const val UNKNOWN = "unknown"
    }
}
