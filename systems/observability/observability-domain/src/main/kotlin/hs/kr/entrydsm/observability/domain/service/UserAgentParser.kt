package hs.kr.entrydsm.observability.domain.service

/**
 * 관리자 화면 표시용 브라우저/OS 이름을 최소한으로 추출한다.
 * ponytail: 정교한 UA 데이터베이스 없이 주요 브라우저/OS만 정규식으로 휴리스틱 인식한다.
 */
object UserAgentParser {
    private val BROWSER_PATTERNS = listOf(
        "Edge" to Regex("Edg/([0-9]+)"),
        "Chrome" to Regex("Chrome/([0-9]+)"),
        "Firefox" to Regex("Firefox/([0-9]+)"),
        "Safari" to Regex("Version/([0-9]+).*Safari"),
    )
    private val OS_PATTERNS = listOf(
        "Windows" to Regex("Windows NT ([0-9.]+)"),
        "Android" to Regex("Android ([0-9.]+)"),
        "iOS" to Regex("(?:iPhone|iPad).*?OS ([0-9_]+)"),
        "macOS" to Regex("Mac OS X ([0-9_]+)"),
    )

    fun browser(userAgent: String?): String? = match(userAgent, BROWSER_PATTERNS)

    fun os(userAgent: String?): String? = match(userAgent, OS_PATTERNS)?.replace('_', '.')

    private fun match(userAgent: String?, patterns: List<Pair<String, Regex>>): String? {
        val ua = userAgent ?: return null
        for ((name, pattern) in patterns) {
            pattern.find(ua)?.let { return "$name ${it.groupValues[1]}" }
        }
        return null
    }
}
