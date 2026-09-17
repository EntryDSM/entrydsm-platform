package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import java.util.UUID

private val unsafeCharacters = Regex("[^A-Za-z0-9._-]")

object FileNaming {

    /** files.object_key·original_name 컬럼 길이. 넘으면 S3 에 올린 뒤 DB 저장에서 실패한다. */
    const val MAX_STORED_NAME_LENGTH = 255

    fun applicationFileName(applicantId: Long, extension: FileExtension): String =
        "application_$applicantId.${extension.value}"

    fun admissionTicketFileName(applicantId: Long): String =
        "admission_ticket_$applicantId.${FileExtension.PDF.value}"

    fun photoFileName(extension: FileExtension): String =
        "photo_${randomToken()}.${extension.value}"

    fun attachmentFileName(originalName: String): String =
        "${randomToken()}_${sanitizeOriginalName(originalName)}"

    /** 밖에 노출하는 파일 ID. 순번 id 대신 쓴다. */
    fun publicId(category: FileCategory): String =
        "${category.prefix}_${randomToken()}"

    fun requireSafeFileName(fileName: String): String {
        if (sanitizeOriginalName(fileName) != fileName) throw InvalidFileNameException(fileName)
        return fileName
    }

    fun requireStorableLength(name: String): String {
        if (name.length > MAX_STORED_NAME_LENGTH) throw InvalidFileNameException(name)
        return name
    }

    fun sanitizeOriginalName(originalName: String): String {
        val baseName = originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        val sanitized = unsafeCharacters.replace(baseName, "_").trimStart('.')
        if (sanitized.isEmpty() || sanitized == "_") throw InvalidFileNameException(originalName)
        return sanitized
    }

    private fun randomToken(): String =
        UUID.randomUUID().toString().replace("-", "")
}
