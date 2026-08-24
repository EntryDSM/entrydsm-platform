package hs.kr.entrydsm.notification.application.port.`in`.command

import hs.kr.entrydsm.notification.domain.model.FaqCategory

data class ReadFaqPageCommand(
    val page: Int = 0,
    val size: Int = 10,
    val category: FaqCategory? = null,
) {
    init {
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size > 0) { "size must be greater than 0" }
    }

    fun offset(): Long = page.toLong() * size.toLong()

    companion object {
        fun of(
            page: Int,
            size: Int,
            category: String? = null,
        ): ReadFaqPageCommand =
            ReadFaqPageCommand(
                page = page,
                size = size,
                category = category?.let(FaqCategory::from),
            )
    }
}
