package hs.kr.entrydsm.notification.domain.model

import java.time.LocalDateTime

/**
 * 자주 묻는 질문입니다. 답변은 관리자가 등록합니다.
 *
 * @property answeredBy 마지막으로 답변을 등록한 관리자. 이관 전 행은 비어 있습니다
 * @property answeredAt 마지막으로 답변을 등록한 시각. 이관 전 행은 비어 있습니다
 */
data class Faq(
    val id: Long,
    val category: String,
    val question: String,
    val answer: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val answeredBy: String? = null,
    val answeredAt: LocalDateTime? = null,
)

