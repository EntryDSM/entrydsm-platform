package hs.kr.entrydsm.configuration.domain.document.command

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.Requester

/** 저장 파일명은 종류에 따라 서비스가 정한다. */
data class UploadFileCommand(
    val category: FileCategory,
    val originalName: String,
    val sizeBytes: Long,
    val requester: Requester,
)
