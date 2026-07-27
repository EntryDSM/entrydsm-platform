package hs.kr.entrydsm.configuration.domain.document

enum class FileExtension(
    val value: String,
    val contentType: String,
    private val aliases: Set<String> = emptySet(),
) {
    PDF("pdf", "application/pdf"),
    HWP("hwp", "application/x-hwp"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    JPG("jpg", "image/jpeg", setOf("jpeg")),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    ;

    companion object {
        val DOCUMENT_FORMATS = setOf(PDF, HWP)

        val IMAGE_FORMATS = setOf(JPG, PNG, WEBP)

        val ATTACHMENT_FORMATS = DOCUMENT_FORMATS + IMAGE_FORMATS + setOf(XLSX, DOCX)

        fun fromFileName(fileName: String): FileExtension? {
            val extension = fileName.substringAfterLast('.', "")
            if (extension.isEmpty()) return null
            return fromExtension(extension)
        }

        fun fromExtension(extension: String): FileExtension? {
            val normalized = extension.removePrefix(".").lowercase()
            return entries.firstOrNull { it.value == normalized || normalized in it.aliases }
        }
    }
}
