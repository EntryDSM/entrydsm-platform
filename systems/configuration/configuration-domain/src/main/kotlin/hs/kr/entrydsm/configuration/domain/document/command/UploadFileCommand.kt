package hs.kr.entrydsm.configuration.domain.document.command

import hs.kr.entrydsm.configuration.domain.document.FileCategory

data class UploadFileCommand(
    val category: FileCategory,
    val originalName: String,
    val sizeBytes: Long,
    val fileName: String? = null,
)
