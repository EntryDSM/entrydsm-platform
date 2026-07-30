package hs.kr.entrydsm.notification.domain.model

import java.time.LocalDateTime

data class Notice(
    val id: Long,
    val title: String,
    val content: String,
    val author: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

