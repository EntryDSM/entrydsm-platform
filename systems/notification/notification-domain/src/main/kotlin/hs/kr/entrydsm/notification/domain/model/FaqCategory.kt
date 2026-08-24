package hs.kr.entrydsm.notification.domain.model

import java.util.Locale

enum class FaqCategory(
    val label: String,
) {
    ADMISSION("입학 문의"),
    CAREER("진로"),
    SCHOOL_LIFE("학교 생활"),
    DORMITORY("기숙사"),
    ETC("기타"),
    ;

    companion object {
        fun from(value: String): FaqCategory =
            entries.firstOrNull { category ->
                category.name == value.uppercase(Locale.ROOT) || category.label == value
            } ?: throw IllegalArgumentException("invalid faq category: $value")
    }
}
