package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.FileAccessor.ADMIN
import hs.kr.entrydsm.configuration.domain.document.FileAccessor.OWNER
import hs.kr.entrydsm.configuration.domain.document.FileAccessor.STUDENT

private const val MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024
private const val MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024
private const val MAX_ATTACHMENT_SIZE_BYTES = 20L * 1024 * 1024

/**
 * 파일 종류. 누가 적재(업로드)하고 누가 다운로드하는지는 [storers], [downloaders] 권한표로만 정한다.
 */
enum class FileCategory(
    val prefix: String,
    val allowedExtensions: Set<FileExtension>,
    val maxSizeBytes: Long,
    val storers: Set<FileAccessor>,
    val downloaders: Set<FileAccessor>,
) {
    APPLICATION(
        "application", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES,
        storers = setOf(ADMIN, OWNER), downloaders = setOf(ADMIN, OWNER),
    ),
    /** 올리지 않고 서버가 만든다. 만들 수 있는 사람은 받을 수 있는 사람과 같다. */
    ADMISSION_TICKET(
        "admission-ticket", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES,
        storers = emptySet(), downloaders = setOf(ADMIN, OWNER),
    ),
    APPLICANT_LIST(
        "applicant-list", setOf(FileExtension.XLSX), MAX_DOCUMENT_SIZE_BYTES,
        storers = setOf(ADMIN), downloaders = setOf(ADMIN),
    ),

    /** 따로 다운로드 API 가 없고, 올린 학생이 업로드 응답으로 URL 을 받는다. */
    PHOTO(
        "photo", FileExtension.imageFormats, MAX_PHOTO_SIZE_BYTES,
        storers = setOf(STUDENT), downloaders = setOf(OWNER),
    ),
    ATTACHMENT(
        "attachment", FileExtension.attachmentFormats, MAX_ATTACHMENT_SIZE_BYTES,
        storers = setOf(ADMIN), downloaders = setOf(ADMIN, STUDENT),
    ),
    GUIDELINE(
        "guideline", FileExtension.attachmentFormats, MAX_ATTACHMENT_SIZE_BYTES,
        storers = setOf(ADMIN), downloaders = setOf(ADMIN, STUDENT),
    ),
    ;

    fun supports(extension: FileExtension): Boolean = extension in allowedExtensions

    fun exceedsMaxSize(sizeBytes: Long): Boolean = sizeBytes > maxSizeBytes

    /** 아직 본인이 없는 파일은 처음 적재하는 학생이 본인이 된다. */
    fun canStore(requester: Requester, ownerUserId: Long?): Boolean =
        storers.allows(requester, ownerUserId ?: requester.userId)

    fun canDownload(requester: Requester, ownerUserId: Long?): Boolean =
        downloaders.allows(requester, ownerUserId)

    fun objectKeyOf(fileName: String): String =
        FileNaming.requireStorableLength("$KEY_ROOT$prefix/${FileNaming.requireSafeFileName(fileName)}")

    fun holds(objectKey: String): Boolean = objectKey.startsWith("$KEY_ROOT$prefix/")

    companion object {
        const val KEY_ROOT = "dsm_Entry/Backend/"
    }
}

private fun Set<FileAccessor>.allows(requester: Requester, ownerUserId: Long?): Boolean =
    when (requester.role) {
        Requester.Role.ADMIN -> ADMIN in this
        Requester.Role.STUDENT -> STUDENT in this || (OWNER in this && ownerUserId == requester.userId)
    }
