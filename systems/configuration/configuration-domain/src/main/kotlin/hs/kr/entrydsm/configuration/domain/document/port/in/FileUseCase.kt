package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FilePage
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import java.io.InputStream

/** 공개 ID 로 찾는 증명사진·첨부·요강. */
interface FileUseCase {
    fun upload(command: UploadFileCommand, content: InputStream): DownloadableFile

    fun find(category: FileCategory, publicId: String, requester: Requester): DownloadableFile

    /** 최근에 올린 것부터. [page] 는 1부터 센다. */
    fun findPage(category: FileCategory, page: Int, size: Int, requester: Requester): FilePage

    fun delete(category: FileCategory, publicId: String, requester: Requester)
}
