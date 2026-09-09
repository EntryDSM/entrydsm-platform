package hs.kr.entrydsm.observability.application.port.out

interface RateLimitPort {
    /** @return windowSeconds 동안 key에 대해 limit회를 초과하지 않았으면 true */
    fun tryAcquire(key: String, limit: Long, windowSeconds: Long): Boolean
}
