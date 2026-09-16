package hs.kr.entrydsm.identity.config.edge

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.Collections
import java.util.Enumeration

/**
 * 신뢰 헤더를 엣지가 정한 값으로 바꾼 요청이다.
 *
 * 서블릿 요청은 헤더를 고칠 수 없어 감싼다. 헤더 이름은 대소문자를 구분하지 않는다.
 */
class TrustedHeaderRequestWrapper(
    request: HttpServletRequest,
    private val overrides: Map<String, String>,
    private val removed: Set<String>,
) : HttpServletRequestWrapper(request) {
    private val overridesByLowerName = overrides.mapKeys { it.key.lowercase() }
    private val removedLowerNames = removed.map { it.lowercase() }.toSet()

    override fun getHeader(name: String): String? {
        val lower = name.lowercase()
        overridesByLowerName[lower]?.let { return it }
        if (lower in removedLowerNames) return null
        return super.getHeader(name)
    }

    override fun getHeaders(name: String): Enumeration<String> {
        val lower = name.lowercase()
        overridesByLowerName[lower]?.let { return Collections.enumeration(listOf(it)) }
        if (lower in removedLowerNames) return Collections.emptyEnumeration()
        return super.getHeaders(name)
    }

    override fun getHeaderNames(): Enumeration<String> {
        val names = super.getHeaderNames().toList()
            .filterNot { it.lowercase() in removedLowerNames || it.lowercase() in overridesByLowerName }
        return Collections.enumeration(names + overrides.keys)
    }
}
