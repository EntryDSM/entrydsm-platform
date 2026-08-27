package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import java.io.InputStream

interface UploadFileUseCase {
    fun upload(command: UploadFileCommand, content: InputStream): FileDocument
}
