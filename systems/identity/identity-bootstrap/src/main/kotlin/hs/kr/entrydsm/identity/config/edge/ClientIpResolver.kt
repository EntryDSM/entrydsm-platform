package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress

/**
 * 클라이언트 IP 를 고른다. gateway 가 쓰던 규칙 그대로다.
 *
 * 바로 앞 연결이 내부 주소일 때만 `X-Forwarded-For` 를 믿고, 그중 가장 오른쪽의 외부 주소를 쓴다.
 * 프록시가 덧붙인 값만 신뢰하기 위해서다.
 */
object ClientIpResolver {
    fun resolve(request: HttpServletRequest): String {
        val peer = request.remoteAddr.orEmpty()
        if (!isInternal(peer)) return peer
        return request.getHeader(EdgeContract.FORWARDED_FOR_HEADER)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.lastOrNull { !isInternal(it) }
            ?: peer
    }

    private fun isInternal(address: String): Boolean =
        try {
            val parsed = InetAddress.getByName(address)
            parsed.isSiteLocalAddress || parsed.isLoopbackAddress || parsed.isLinkLocalAddress
        } catch (exception: Exception) {
            // 주소 형식이 아니면 외부 값으로 본다. gateway 와 같은 판단이다.
            false
        }
}
