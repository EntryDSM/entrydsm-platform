package hs.kr.entrydsm.notification.adapterin.web.dto.response

import java.time.LocalDateTime

data class NoticeSummaryResponse(
    val noticeId: Long,
    val title: String,
    val author: String,
    val createdAt: LocalDateTime,
)

data class NoticeDetailResponse(
    val noticeId: Long,
    val title: String,
    val content: String,
    val author: String,
    val viewCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

