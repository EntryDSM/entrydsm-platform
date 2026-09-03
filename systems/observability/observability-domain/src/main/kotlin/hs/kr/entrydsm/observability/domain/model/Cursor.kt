package hs.kr.entrydsm.observability.domain.model

import hs.kr.entrydsm.observability.domain.enum.ErrorCode
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import java.util.Base64

/**
 * 로그 목록 조회의 페이지네이션 커서입니다. 정렬 기준 score(발생 시각 epoch millis)와
 * 마지막으로 조회한 항목의 id를 불투명 토큰으로 인코딩합니다.
 */
data class Cursor(
    val lastScore: Long,
    val lastId: String,
) {
    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$lastScore:$lastId".toByteArray())

    companion object {
        fun decode(raw: String): Cursor {
            val decoded = runCatching { String(Base64.getUrlDecoder().decode(raw)) }
                .getOrElse { throw MonitorDomainException(ErrorCode.INVALID_CURSOR) }
            val parts = decoded.split(":", limit = 2)
            val score = parts.getOrNull(0)?.toLongOrNull()
            val id = parts.getOrNull(1)
            if (score == null || id.isNullOrEmpty()) {
                throw MonitorDomainException(ErrorCode.INVALID_CURSOR)
            }
            return Cursor(score, id)
        }
    }
}
