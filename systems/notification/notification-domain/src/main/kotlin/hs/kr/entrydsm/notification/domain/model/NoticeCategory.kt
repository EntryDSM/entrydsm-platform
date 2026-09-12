package hs.kr.entrydsm.notification.domain.model

import java.util.Locale

/**
 * 공지 분류입니다.
 *
 * @property label 공지 목록·상세에 노출하는 한글 이름
 * @property specName Notion "공지 등록" 명세가 `division` 예시로 쓰는 영문 이름
 */
enum class NoticeCategory(
    val label: String,
    val specName: String,
) {
    ADMISSION_NOTICE("입학 공지사항", "Admissions Notice"),
    PROSPECTIVE_STUDENT("예비 신입생 안내", "Prospective Students Notice"),
    ;

    companion object {
        /**
         * 분류 이름을 [NoticeCategory] 로 바꿉니다.
         *
         * 열거형 이름([name]), 한글 이름([label]), 명세의 영문 이름([specName]) 을 모두 받습니다.
         * 명세와 실제 값이 어긋나 있어(#140 리뷰) 명세대로 보낸 요청도 거절하지 않고 치환합니다.
         */
        fun from(value: String): NoticeCategory {
            val normalized = value.trim()
            return entries.firstOrNull { category ->
                category.name == normalized.uppercase(Locale.ROOT) ||
                    category.label == normalized ||
                    category.specName.equals(normalized, ignoreCase = true)
            } ?: throw IllegalArgumentException("invalid notice category: $value")
        }
    }
}
