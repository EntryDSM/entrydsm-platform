package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.FileAccessor.ADMIN
import hs.kr.entrydsm.configuration.domain.document.FileAccessor.OWNER
import hs.kr.entrydsm.configuration.domain.document.FileAccessor.STUDENT

private const val MAX_DOCUMENT_SIZE_BYTES = 10L * 1024 * 1024
private const val MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024
private const val MAX_ATTACHMENT_SIZE_BYTES = 20L * 1024 * 1024

/**
 * 파일 종류. 누가 적재(업로드)하고 누가 다운로드하는지는 [storers], [downloaders] 권한표로만 정한다.
 * 원서·수험표는 지원자 ID 로, 나머지는 공개 ID 로 찾는다.
 */
enum class FileCategory(
    val prefix: String,
    val allowedExtensions: Set<FileExtension>,
    val maxSizeBytes: Long,
    val storers: Set<FileAccessor>,
    val downloaders: Set<FileAccessor>,
) {
    /** 요강 <서식 1> 양식으로 서버가 만든다. 올리지 않는다. 본인과 관리자가 받는다. */
    APPLICATION(
        "application", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES,
        storers = emptySet(), downloaders = setOf(ADMIN, OWNER),
    ),
    /** 올리지 않고 서버가 만든다. 만들 수 있는 사람은 받을 수 있는 사람과 같다. */
    ADMISSION_TICKET(
        "admission-ticket", FileExtension.documentFormats, MAX_DOCUMENT_SIZE_BYTES,
        storers = emptySet(), downloaders = setOf(ADMIN, OWNER),
    ),
    PHOTO(
        "photo", FileExtension.imageFormats, MAX_PHOTO_SIZE_BYTES,
        storers = setOf(STUDENT), downloaders = setOf(ADMIN, OWNER),
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

    /** 이 종류의 객체가 모이는 저장소 폴더 */
    val legacyKeyPrefix: String
        get() = "$LEGACY_KEY_ROOT$prefix/"

    fun keyPrefix(environment: String): String = "${keyRoot(environment)}$prefix/"

    fun supports(extension: FileExtension): Boolean = extension in allowedExtensions

    fun exceedsMaxSize(sizeBytes: Long): Boolean = sizeBytes > maxSizeBytes

    fun canStore(requester: Requester, ownerUserId: Long?): Boolean =
        storers.allows(requester, ownerUserId)

    fun canDownload(requester: Requester, ownerUserId: Long?): Boolean =
        downloaders.allows(requester, ownerUserId)

    /** 적재할 수 있는 사람이 지운다. 다만 학생은 자기가 올린 파일만 지운다. */
    fun canDelete(requester: Requester, ownerUserId: Long?): Boolean =
        canStore(requester, ownerUserId) && (requester.role == Requester.Role.ADMIN || ownerUserId == requester.userId)

    fun objectKeyOf(fileName: String, environment: String = "stag"): String =
        FileNaming.requireStorableLength("${keyPrefix(environment)}${FileNaming.requireSafeFileName(fileName)}")

    fun holds(objectKey: String): Boolean =
        objectKey.startsWith(legacyKeyPrefix) ||
            objectKey.startsWith("dsm_Entry/backend/prod/$prefix/") ||
            objectKey.startsWith("dsm_Entry/backend/stag/$prefix/")

    companion object {
        const val LEGACY_KEY_ROOT = "dsm_Entry/Backend/"

        fun keyRoot(environment: String): String {
            require(environment == "prod" || environment == "stag") { "STORAGE_ENV must be 'prod' or 'stag'" }
            return "dsm_Entry/backend/$environment/"
        }
    }
}

private fun Set<FileAccessor>.allows(requester: Requester, ownerUserId: Long?): Boolean =
    when (requester.role) {
        Requester.Role.ADMIN -> ADMIN in this
        Requester.Role.STUDENT -> STUDENT in this || (OWNER in this && ownerUserId == requester.userId)
    }
