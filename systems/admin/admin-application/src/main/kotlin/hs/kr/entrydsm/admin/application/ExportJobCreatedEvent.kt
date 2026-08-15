package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.model.ExportJob

/**
 * 내보내기 작업이 접수되었음을 알립니다. 처리기는 커밋 이후에 이 이벤트를 받습니다.
 */
data class ExportJobCreatedEvent(val job: ExportJob)
