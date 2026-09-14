package hs.kr.entrydsm.configuration.domain.document.command

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.Requester

data class UploadFileCommand(
    val category: FileCategory,
    val originalName: String,
    val fileName: String,
    val sizeBytes: Long,
    val requester: Requester,
    /** 원서·수험표의 수험번호. 본인 판정 기준이다. */
    val receiptCode: String? = null,
)
