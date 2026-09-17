package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import org.springframework.web.multipart.MultipartFile

fun MultipartFile.toUploadCommand(category: FileCategory, requester: Requester) = UploadFileCommand(
    category = category,
    originalName = originalFilename.orEmpty(),
    sizeBytes = size,
    requester = requester,
)
