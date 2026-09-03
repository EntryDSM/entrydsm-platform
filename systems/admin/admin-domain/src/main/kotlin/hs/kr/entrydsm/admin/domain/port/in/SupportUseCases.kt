package hs.kr.entrydsm.admin.domain.port.`in`

import hs.kr.entrydsm.admin.domain.command.AnswerQuestionCommand
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.command.CreateNoticeCommand
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.ExportJobView
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer

interface CreateExportUseCase {
    fun create(command: CreateExportCommand): ExportJob
}

interface ReadExportUseCase {
    /**
     * 작업 상태를 조회합니다. 완료된 작업이면 서명된 다운로드 링크를 함께 채웁니다.
     */
    fun findById(exportJobId: String): ExportJobView
}

interface CreateNoticeUseCase {
    fun create(command: CreateNoticeCommand): Notice
}

interface AnswerQuestionUseCase {
    fun answer(command: AnswerQuestionCommand): QuestionAnswer
}
