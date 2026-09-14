package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val safeIdentifier = Regex("[A-Za-z0-9_-]+")
private val unsafeCharacters = Regex("[^A-Za-z0-9._-]")
private val applicantListDate = DateTimeFormatter.ofPattern("yyyyMMdd")

object FileNaming {

    /** files.object_key·original_name 컬럼 길이. 넘으면 S3 에 올린 뒤 DB 저장에서 실패한다. */
    const val MAX_STORED_NAME_LENGTH = 255

    fun applicationFileName(receiptCode: String, extension: FileExtension): String =
        "application_${requireIdentifier(receiptCode)}.${extension.value}"

    fun admissionTicketFileName(receiptCode: String, extension: FileExtension): String =
        "admission_ticket_${requireIdentifier(receiptCode)}.${extension.value}"

    fun applicantListFileName(date: LocalDate): String =
        "applicants_${applicantListDate.format(date)}.${FileExtension.XLSX.value}"

    fun photoFileName(extension: FileExtension): String =
        "photo_${randomToken()}.${extension.value}"

    fun attachmentFileName(originalName: String): String =
        "${randomToken()}_${sanitizeOriginalName(originalName)}"

    fun requireIdentifier(value: String): String {
        if (!safeIdentifier.matches(value)) throw InvalidFileNameException(value)
        return value
    }

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
