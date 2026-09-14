package hs.kr.entrydsm.notification.application.port.`in`.command

import hs.kr.entrydsm.notification.domain.model.NoticeCategory

/**
 * 공지를 수정합니다. null 인 필드는 기존 값을 유지합니다.
 *
 * @property attachmentIds 교체할 첨부 문서 식별자 목록. 빈 목록이면 첨부를 모두 뗀다
 */
data class UpdateNoticeCommand(
    val noticeId: Long,
    val title: String? = null,
    val content: String? = null,
    val category: NoticeCategory? = null,
    val isPinned: Boolean? = null,
    val attachmentIds: List<String>? = null,
) {
    init {
        require(title == null || (title.isNotBlank() && title.length <= 255)) { "title must be 1..255 characters" }
        require(content == null || content.isNotBlank()) { "content must not be blank" }
    }
}
