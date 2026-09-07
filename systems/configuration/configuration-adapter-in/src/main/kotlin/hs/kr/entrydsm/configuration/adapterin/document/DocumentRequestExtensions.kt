package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import org.springframework.web.multipart.MultipartFile

class InvalidDownloadFormatException(format: String, category: FileCategory) :
    RuntimeException(
        "Unsupported download format for ${category.name}: $format " +
            "(allowed: ${category.allowedExtensions.joinToString { it.value }})"
    )

fun MultipartFile.requireExtension(category: FileCategory): FileExtension =
    FileExtension.fromFileName(originalName())?.takeIf(category::supports)
        ?: throw InvalidFileFormatException(originalName(), category)

fun MultipartFile.toUploadCommand(category: FileCategory, fileName: String) = UploadFileCommand(
    category = category,
    originalName = originalName(),
    fileName = fileName,
    sizeBytes = size,
)

fun requireDownloadFormat(format: String, category: FileCategory): FileExtension =
    FileExtension.fromExtension(format)?.takeIf(category::supports)
        ?: throw InvalidDownloadFormatException(format, category)

private fun MultipartFile.originalName(): String = originalFilename.orEmpty()
