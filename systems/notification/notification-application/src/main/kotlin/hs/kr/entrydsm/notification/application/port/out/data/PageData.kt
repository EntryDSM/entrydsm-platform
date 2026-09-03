package hs.kr.entrydsm.notification.application.port.out.data

data class PageData<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int =
        if (totalElements == 0L) {
            0
        } else {
            ((totalElements - 1) / size).toInt() + 1
        }

    val last: Boolean = page >= totalPages - 1
}
