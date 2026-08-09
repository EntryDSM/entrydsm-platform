package hs.kr.entrydsm.observability.domain.service

import hs.kr.entrydsm.observability.domain.enum.ServiceName

object ServiceLabels {
    private val LABELS = mapOf(
        ServiceName.IDENTITY to "유저",
        ServiceName.AUTH to "인증",
        ServiceName.APPLICATION to "접수",
        ServiceName.EVALUATION to "심사",
        ServiceName.DOCUMENT to "서류",
        ServiceName.NOTIFICATION to "알림",
        ServiceName.SCHEDULE to "일정",
    )

    fun of(service: ServiceName): String = LABELS.getValue(service)
}
