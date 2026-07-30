package hs.kr.entrydsm.notification.application.port.`in`.command

data class ReadNotificationPageCommand(
    val page: Int = 0,
    val size: Int = 10,
)

