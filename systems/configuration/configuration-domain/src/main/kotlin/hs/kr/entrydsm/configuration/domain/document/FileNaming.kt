package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val SAFE_IDENTIFIER = Regex("[A-Za-z0-9_-]+")
private val UNSAFE_CHARACTERS = Regex("[^A-Za-z0-9._-]")
private val APPLICANT_LIST_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

object FileNaming {

    fun applicationFileName(receiptCode: String, extension: FileExtension): String =
        "application_${requireIdentifier(receiptCode)}.${extension.value}"

    fun admissionTicketFileName(receiptCode: String, extension: FileExtension): String =
        "admission_ticket_${requireIdentifier(receiptCode)}.${extension.value}"

    fun applicantListFileName(date: LocalDate): String =
        "applicants_${APPLICANT_LIST_DATE.format(date)}.${FileExtension.XLSX.value}"

    fun photoFileName(extension: FileExtension): String =
        "photo_${randomToken()}.${extension.value}"

    fun attachmentFileName(originalName: String): String =
        "${randomToken()}_${sanitizeOriginalName(originalName)}"

    fun requireIdentifier(value: String): String {
        if (!SAFE_IDENTIFIER.matches(value)) throw InvalidFileNameException(value)
        return value
    }

    fun requireSafeFileName(fileName: String): String {
        if (sanitizeOriginalName(fileName) != fileName) throw InvalidFileNameException(fileName)
        return fileName
    }

    fun sanitizeOriginalName(originalName: String): String {
        val baseName = originalName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
        val sanitized = UNSAFE_CHARACTERS.replace(baseName, "_").trimStart('.')
        if (sanitized.isEmpty() || sanitized == "_") throw InvalidFileNameException(originalName)
        return sanitized
    }

    private fun randomToken(): String =
        UUID.randomUUID().toString().replace("-", "")
}
