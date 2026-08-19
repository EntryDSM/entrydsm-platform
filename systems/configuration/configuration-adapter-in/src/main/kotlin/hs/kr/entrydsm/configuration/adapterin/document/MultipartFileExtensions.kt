package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import org.springframework.web.multipart.MultipartFile

fun MultipartFile.requireExtension(category: FileCategory): FileExtension =
    FileExtension.fromFileName(originalName())?.takeIf(category::supports)
        ?: throw InvalidFileFormatException(originalName(), category)

fun MultipartFile.toUploadCommand(category: FileCategory, fileName: String) = UploadFileCommand(
    category = category,
    originalName = originalName(),
    fileName = fileName,
    sizeBytes = size,
)

private fun MultipartFile.originalName(): String = originalFilename.orEmpty()
