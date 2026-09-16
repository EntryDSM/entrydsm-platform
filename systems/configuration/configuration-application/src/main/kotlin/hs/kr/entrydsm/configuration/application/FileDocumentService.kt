package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.FilePage
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ApplicantFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.FileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.time.Instant
import java.util.Base64

class FileDocumentService(
    private val storagePort: StoragePort,
    private val fileDocumentRepository: FileDocumentRepository,
    private val presignExpirySeconds: Long,
    private val applicantPort: ApplicantPort,
    private val pdfRenderPort: PdfRenderPort,
    private val admissionYear: Int,
) : ApplicantFileUseCase,
    FileUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun uploadApplication(applicantId: Long, command: UploadFileCommand, content: InputStream): DownloadableFile {
        val category = FileCategory.APPLICATION
        val extension = validate(command)
        val applicant = requireApplicant(applicantId, command.requester, category::canStore)
        val fileName = FileNaming.applicationFileName(applicantId, extension)
        return downloadable(
            store(category, fileName, command.originalName, extension, command.sizeBytes, content, applicant.userId)
        )
    }

    override fun findApplication(applicantId: Long, requester: Requester): DownloadableFile? {
        val category = FileCategory.APPLICATION
        requireApplicant(applicantId, requester, category::canDownload)
        return FileExtension.documentFormats
            .mapNotNull { fileDocumentRepository.findByObjectKey(category.objectKeyOf(FileNaming.applicationFileName(applicantId, it))) }
            .maxByOrNull { it.createdAt ?: Instant.EPOCH }
            ?.let(::downloadable)
    }

    override fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile {
        val category = FileCategory.ADMISSION_TICKET
        val applicant = requireApplicant(applicantId, requester, category::canDownload)
        val pdf = pdfRenderPort.render(
            AdmissionTicketHtml.render(
                admissionYear, applicant,
                photoDataUri = applicant.photoFileId?.let { photoDataUri(it, applicant.userId) },
            )
        )
        val fileName = FileNaming.admissionTicketFileName(applicantId)
        return downloadable(
            store(category, fileName, fileName, FileExtension.PDF, pdf.size.toLong(), pdf.inputStream(), applicant.userId)
        )
    }

    override fun upload(command: UploadFileCommand, content: InputStream): DownloadableFile {
        val category = command.category
        val extension = validate(command)
        if (!category.canStore(command.requester, ownerUserId = null)) throw DocumentAccessDeniedException()
        val fileName = when (category) {
            FileCategory.PHOTO -> FileNaming.photoFileName(extension)
            FileCategory.ATTACHMENT, FileCategory.GUIDELINE -> FileNaming.attachmentFileName(command.originalName)
            FileCategory.APPLICATION, FileCategory.ADMISSION_TICKET ->
                throw IllegalArgumentException("$category is stored per applicant")
        }
        return downloadable(
            store(category, fileName, command.originalName, extension, command.sizeBytes, content, command.requester.studentId)
        )
    }

    override fun find(category: FileCategory, publicId: String, requester: Requester): DownloadableFile {
        val document = requireFile(category, publicId)
        if (!category.canDownload(requester, document.ownerUserId)) throw DocumentAccessDeniedException()
        return downloadable(document)
    }

    override fun findPage(category: FileCategory, page: Int, size: Int, requester: Requester): FilePage {
        if (!category.canDownload(requester, ownerUserId = null)) throw DocumentAccessDeniedException()
        return FilePage(
            items = fileDocumentRepository.findPage(category, page, size).map(::downloadable),
            totalElements = fileDocumentRepository.count(category),
        )
    }

    override fun delete(category: FileCategory, publicId: String, requester: Requester) {
        val document = requireFile(category, publicId)
        if (!category.canDelete(requester, document.ownerUserId)) throw DocumentAccessDeniedException()
        // 행을 먼저 지워 API 에서는 바로 사라진다. 객체 삭제가 실패하면 저장소에만 남는다.
        fileDocumentRepository.deleteByObjectKey(document.objectKey)
        deleteQuietly(document.objectKey)
    }

    /**
     * application 에 원서 주인을 묻고 권한을 판정한다. 권한을 먼저 보므로 학생에게 남의 지원자는
     * 없어도 403 이라, applicant id 를 훑어 지원자가 있는지 알 수 없다. 관리자에게 없는 지원자는 404 다.
     */
    private fun requireApplicant(
        applicantId: Long,
        requester: Requester,
        allowed: (Requester, Long?) -> Boolean,
    ): Applicant {
        val applicant = applicantPort.findById(applicantId)
        if (!allowed(requester, applicant?.userId)) throw DocumentAccessDeniedException()
        return applicant ?: throw ApplicantNotFoundException(applicantId)
    }

    private fun requireFile(category: FileCategory, publicId: String): FileDocument =
        fileDocumentRepository.findByPublicId(publicId)?.takeIf { category.holds(it.objectKey) }
            ?: throw FileDocumentNotFoundException(publicId)

    private fun validate(command: UploadFileCommand): FileExtension {
        val extension = FileExtension.fromFileName(command.originalName)?.takeIf(command.category::supports)
            ?: throw InvalidFileFormatException(command.originalName, command.category)
        FileNaming.requireStorableLength(command.originalName)
        if (command.category.exceedsMaxSize(command.sizeBytes)) {
            throw FileTooLargeException(command.sizeBytes, command.category.maxSizeBytes)
        }
        return extension
    }

    private fun store(
        category: FileCategory,
        fileName: String,
        originalName: String,
        extension: FileExtension,
        sizeBytes: Long,
        content: InputStream,
        ownerUserId: Long?,
    ): FileDocument {
        val objectKey = category.objectKeyOf(fileName)
        // 같은 키를 덮어쓴 경우 보상 삭제가 이전 파일까지 지우면 안 된다.
        val replacedExistingObject = storagePort.exists(objectKey)
        val stored = storagePort.upload(objectKey, extension.contentType, sizeBytes, content)

        return try {
            fileDocumentRepository.save(
                FileDocument(
                    publicId = FileNaming.publicId(category),
                    originalName = originalName,
                    objectKey = stored.objectKey,
                    bucket = stored.bucket,
                    contentType = extension.contentType,
                    sizeBytes = sizeBytes,
                    checksum = stored.checksum,
                    ownerUserId = ownerUserId,
                )
            )
        } catch (e: RuntimeException) {
            if (!replacedExistingObject) deleteQuietly(objectKey)
            throw e
        }
    }

    private fun downloadable(document: FileDocument) = DownloadableFile(
        document = document,
        downloadUrl = storagePort.issueDownloadUrl(document.objectKey, presignExpirySeconds),
        expiresIn = presignExpirySeconds,
    )

    /**
     * 원서에 적힌 사진 ID 는 학생이 보낸 값이라, 그 학생이 올린 사진일 때만 수험표에 넣는다.
     *
     * ponytail: webp 사진은 openhtmltopdf(ImageIO)가 읽지 못해 빈 칸으로 찍힌다. 필요해지면 webp 디코더를 붙인다.
     */
    private fun photoDataUri(photoFileId: String, ownerUserId: Long): String? {
        val photo = fileDocumentRepository.findByPublicId(photoFileId)
            ?.takeIf { FileCategory.PHOTO.holds(it.objectKey) && it.ownerUserId == ownerUserId }
            ?: return null
        return "data:${photo.contentType};base64," + Base64.getEncoder().encodeToString(storagePort.download(photo.objectKey))
    }

    private fun deleteQuietly(objectKey: String) {
        runCatching { storagePort.delete(objectKey) }
            .onFailure { log.warn("Failed to delete stored object: {}", objectKey, it) }
    }
}
