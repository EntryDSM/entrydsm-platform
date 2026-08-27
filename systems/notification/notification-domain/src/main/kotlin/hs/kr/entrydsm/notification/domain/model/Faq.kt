package hs.kr.entrydsm.notification.domain.model

import java.time.LocalDateTime

data class Faq(
    val id: Long,
    val category: String,
    val question: String,
    val answer: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

