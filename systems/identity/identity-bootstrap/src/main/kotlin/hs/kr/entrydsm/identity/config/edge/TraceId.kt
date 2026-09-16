package hs.kr.entrydsm.identity.config.edge

/** 요청을 로그에서 이어 보기 위한 식별자. gateway 의 규칙을 그대로 옮겼다. */
@JvmInline
value class TraceId private constructor(val value: String) {
    companion object {
        const val HEADER_NAME = "X-Trace-Id"
        const val MAX_LENGTH = 128
        private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        fun from(value: String?): TraceId {
            require(!value.isNullOrBlank()) { "$HEADER_NAME must not be blank" }
            require(value.length <= MAX_LENGTH && PATTERN.matches(value)) {
                "$HEADER_NAME must contain only letters, digits, '.', '_' or '-' and be 1-$MAX_LENGTH characters"
            }
            return TraceId(value)
        }

        fun generated(generator: () -> String): TraceId = from(generator())
    }
}
