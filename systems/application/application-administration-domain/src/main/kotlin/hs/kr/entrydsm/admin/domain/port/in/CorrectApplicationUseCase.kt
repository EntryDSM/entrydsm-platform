package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand

fun interface CorrectApplicationUseCase {
    /** 수정자는 인증된 RPC 메타데이터에서 전달하며 요청 본문에서 받지 않는다. */
    fun correct(command: CorrectApplicationCommand, editorId: String): ApplicationCorrectionResult

    /** 조회와 정정은 동일한 상태 정책을 사용한다. */
    fun editableFields(applicantId: Long): EditableApplicationFields { error("지원하지 않는 필드 조회") }
}

data class ApplicationCorrectionResult(val applicantId: Long, val version: Long)
data class EditableApplicationFields(val editable: Boolean, val fields: List<String>, val reason: String?)
