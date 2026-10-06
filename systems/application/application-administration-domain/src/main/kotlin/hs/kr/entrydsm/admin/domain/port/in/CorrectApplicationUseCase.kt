package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand

fun interface CorrectApplicationUseCase {
    /** 수정자는 인증된 RPC 메타데이터에서 전달하며 요청 본문에서 받지 않는다. */
    fun correct(command: CorrectApplicationCommand, editorId: String): ApplicationCorrectionResult
}

data class ApplicationCorrectionResult(val applicantId: Long, val version: Long)
