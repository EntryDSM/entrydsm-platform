package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand

interface IssueDownloadUrlUseCase {
    fun issueByCommand(command: IssueDownloadUrlCommand): DownloadUrl
    fun issueById(id: Long): DownloadUrl
}
