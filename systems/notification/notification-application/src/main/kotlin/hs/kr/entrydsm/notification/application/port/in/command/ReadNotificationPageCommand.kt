package hs.kr.entrydsm.notification.application.port.`in`.command

data class ReadNotificationPageCommand(
    val page: Int = 0,
    val size: Int = 10,
) {
    init {
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size > 0) { "size must be greater than 0" }
    }

    fun offset(): Long = page.toLong() * size.toLong()
}
