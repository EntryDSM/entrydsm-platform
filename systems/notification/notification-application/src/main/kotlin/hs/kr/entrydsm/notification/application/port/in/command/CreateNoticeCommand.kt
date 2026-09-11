package hs.kr.entrydsm.notification.application.port.`in`.command

import hs.kr.entrydsm.notification.domain.model.NoticeCategory

data class CreateNoticeCommand(
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val author: String,
    val isPinned: Boolean = false,
    val attachmentIds: List<String> = emptyList(),
) {
    init {
        require(title.isNotBlank() && title.length <= 255) { "title must be 1..255 characters" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(author.isNotBlank() && author.length <= 50) { "author must be 1..50 characters" }
    }
}
