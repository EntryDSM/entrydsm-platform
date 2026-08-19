package hs.kr.entrydsm.notification.domain.model

import java.util.Locale

enum class NoticeCategory(
    val label: String,
) {
    ADMISSION_NOTICE("입학 공지사항"),
    PROSPECTIVE_STUDENT("예비 신입생 안내"),
    ;

    companion object {
        fun from(value: String): NoticeCategory =
            entries.firstOrNull { category ->
                category.name == value.uppercase(Locale.ROOT) || category.label == value
            } ?: throw IllegalArgumentException("invalid notice category: $value")
    }
}
