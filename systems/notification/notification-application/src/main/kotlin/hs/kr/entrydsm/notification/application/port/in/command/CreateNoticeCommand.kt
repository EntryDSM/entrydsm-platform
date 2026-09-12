package hs.kr.entrydsm.notification.application.port.`in`.command

import hs.kr.entrydsm.notification.domain.model.NoticeCategory

data class CreateNoticeCommand(
    val title: String,
    val content: String,
    val category: NoticeCategory,
    val author: String,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(author.isNotBlank()) { "author must not be blank" }
    }
}
