package hs.kr.entrydsm.configuration.domain.document.command

import hs.kr.entrydsm.configuration.domain.document.FileCategory

data class IssueDownloadUrlCommand(
    val category: FileCategory,
    val fileName: String,
)
