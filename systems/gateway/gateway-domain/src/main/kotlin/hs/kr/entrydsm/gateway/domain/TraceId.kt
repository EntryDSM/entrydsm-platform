package hs.kr.entrydsm.gateway.domain

@JvmInline
value class TraceId private constructor(val value: String) {
    companion object {
        const val HEADER_NAME = "X-Trace-Id"
        const val MAX_LENGTH = 128
        private val PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        fun from(value: String?): TraceId {
            require(!value.isNullOrBlank()) {
                "$HEADER_NAME must not be blank"
            }
            require(value.length <= MAX_LENGTH && PATTERN.matches(value)) {
                "$HEADER_NAME must contain only letters, digits, '.', '_' or '-' and be 1-$MAX_LENGTH characters: $value"
            }
            return TraceId(value)
        }

        fun generated(generator: () -> String): TraceId = from(generator())
    }
}
