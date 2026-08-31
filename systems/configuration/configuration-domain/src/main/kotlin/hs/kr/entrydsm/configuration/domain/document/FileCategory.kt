package hs.kr.entrydsm.configuration.domain.document

private const val MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024
private const val MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024
private const val MAX_ATTACHMENT_SIZE_BYTES = 20L * 1024 * 1024

enum class FileCategory(
    val prefix: String,
    val allowedExtensions: Set<FileExtension>,
    val maxSizeBytes: Long,
) {
    APPLICATION("application", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES),
    ADMISSION_TICKET("admission-ticket", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES),
    APPLICANT_LIST("applicant-list", setOf(FileExtension.XLSX), MAX_DOCUMENT_SIZE_BYTES),
    PHOTO("photo", FileExtension.imageFormats, MAX_PHOTO_SIZE_BYTES),
    ATTACHMENT("attachment", FileExtension.attachmentFormats, MAX_ATTACHMENT_SIZE_BYTES),
    GUIDELINE("guideline", FileExtension.attachmentFormats, MAX_ATTACHMENT_SIZE_BYTES),
    ;

    fun supports(extension: FileExtension): Boolean = extension in allowedExtensions

    fun exceedsMaxSize(sizeBytes: Long): Boolean = sizeBytes > maxSizeBytes

    fun objectKeyOf(fileName: String): String =
        "$KEY_ROOT$prefix/${FileNaming.requireSafeFileName(fileName)}"

    companion object {
        const val KEY_ROOT = "dsm_Entry/Backend/"
    }
}
