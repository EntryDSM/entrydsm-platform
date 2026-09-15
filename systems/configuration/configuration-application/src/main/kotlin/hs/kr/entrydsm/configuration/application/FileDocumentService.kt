package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.GenerateAdmissionTicketUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
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
) : UploadFileUseCase,
    GenerateAdmissionTicketUseCase,
    IssueDownloadUrlUseCase,
    ReadFileUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun upload(command: UploadFileCommand, content: InputStream): FileDocument {
        val extension = resolveExtension(command)
        FileNaming.requireStorableLength(command.originalName)
        if (command.category.exceedsMaxSize(command.sizeBytes)) {
            throw FileTooLargeException(command.sizeBytes, command.category.maxSizeBytes)
        }

        val objectKey = command.category.objectKeyOf(command.fileName)
        val ownerUserId = ownerOf(command.receiptCode, objectKey)
        if (!command.category.canStore(command.requester, ownerUserId)) throw DocumentAccessDeniedException()
        return store(
            objectKey, command.originalName, extension, command.sizeBytes, content,
            // 관리자가 덮어써도 본인은 그대로 남긴다.
            ownerUserId = ownerUserId ?: command.requester.studentId,
        )
    }

    override fun generateAdmissionTicket(receiptCode: String, requester: Requester): FileDocument {
        val category = FileCategory.ADMISSION_TICKET
        val fileName = FileNaming.admissionTicketFileName(receiptCode, FileExtension.PDF)
        val objectKey = category.objectKeyOf(fileName)
        val ownerUserId = ownerOf(receiptCode, objectKey)
        // 다운로드처럼 권한을 먼저 봐서, 남의 수험번호는 원서가 없어도 403 이다.
        requireDownloadable(category, requester, ownerUserId)
        val applicant = ownerUserId?.let(applicantPort::findByUserId)
        if (ownerUserId == null || applicant == null) throw ApplicantNotFoundException(receiptCode)

        val pdf = pdfRenderPort.render(
            AdmissionTicketHtml.render(
                admissionYear, receiptCode, applicant,
                photoDataUri = applicant.photoFileId?.let { photoDataUri(it, ownerUserId) },
            )
        )
        return store(objectKey, fileName, FileExtension.PDF, pdf.size.toLong(), pdf.inputStream(), ownerUserId)
    }

    override fun issueByCommand(command: IssueDownloadUrlCommand): DownloadUrl {
        val fileName = FileNaming.requireSafeFileName(command.fileName)
        val objectKey = command.category.objectKeyOf(fileName)
        requireDownloadable(command.category, command.requester, ownerOf(command.receiptCode, objectKey))
        if (!storagePort.exists(objectKey)) throw FileDocumentNotFoundException(objectKey)
        return DownloadUrl(
            fileName = fileName,
            downloadUrl = storagePort.issueDownloadUrl(objectKey, presignExpirySeconds),
            expiresIn = presignExpirySeconds,
        )
    }

    override fun issueById(category: FileCategory, id: Long, requester: Requester): DownloadUrl {
        // id 는 순번이라 종류를 확인하지 않으면 요강 경로로 원서·지원자 목록까지 내주게 된다.
        val fileDocument = fileDocumentRepository.findById(id)?.takeIf { category.holds(it.objectKey) }
            ?: throw FileDocumentNotFoundException("${category.name} id=$id")
        requireDownloadable(category, requester, fileDocument.ownerUserId)
        return DownloadUrl(
            fileName = fileDocument.originalName,
            downloadUrl = storagePort.issueDownloadUrl(fileDocument.objectKey, presignExpirySeconds),
            expiresIn = presignExpirySeconds,
        )
    }

    override fun findById(id: Long): FileDocument =
        fileDocumentRepository.findById(id) ?: throw FileDocumentNotFoundException("id=$id")

    override fun findApplication(receiptCode: String, requester: Requester): FileDocument? {
        val applications = applicationsOf(receiptCode)
        if (applications.isEmpty()) return null
        requireDownloadable(FileCategory.APPLICATION, requester, applications.firstNotNullOfOrNull { it.ownerUserId })
        return applications.maxBy { it.createdAt ?: Instant.EPOCH }
    }

    override fun existsById(id: Long): Boolean =
        fileDocumentRepository.existsById(id)

    /** 원서·수험표의 본인은 그 수험번호로 원서를 적재한 학생이고, 그 밖의 파일은 올린 학생이다. */
    private fun ownerOf(receiptCode: String?, objectKey: String): Long? =
        receiptCode?.let { applicationsOf(it).firstNotNullOfOrNull(FileDocument::ownerUserId) }
            ?: fileDocumentRepository.findByObjectKey(objectKey)?.ownerUserId

    private fun applicationsOf(receiptCode: String): List<FileDocument> =
        FileExtension.documentFormats.mapNotNull { extension ->
            fileDocumentRepository.findByObjectKey(
                FileCategory.APPLICATION.objectKeyOf(FileNaming.applicationFileName(receiptCode, extension))
            )
        }

    private fun store(
        objectKey: String,
        originalName: String,
        extension: FileExtension,
        sizeBytes: Long,
        content: InputStream,
        ownerUserId: Long?,
    ): FileDocument {
        // 같은 키를 덮어쓴 경우 보상 삭제가 이전 파일까지 지우면 안 된다.
        val replacedExistingObject = storagePort.exists(objectKey)
        val stored = storagePort.upload(objectKey, extension.contentType, sizeBytes, content)

        return try {
            fileDocumentRepository.save(
                FileDocument(
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
            if (!replacedExistingObject) deleteOrphan(objectKey)
            throw e
        }
    }

    /**
     * 원서에 적힌 사진 id 는 학생이 보낸 값이라, 그 학생이 올린 사진일 때만 수험표에 넣는다.
     *
     * ponytail: webp 사진은 openhtmltopdf(ImageIO)가 읽지 못해 빈 칸으로 찍힌다. 필요해지면 webp 디코더를 붙인다.
     */
    private fun photoDataUri(photoFileId: Long, ownerUserId: Long): String? {
        val photo = fileDocumentRepository.findById(photoFileId)
            ?.takeIf { FileCategory.PHOTO.holds(it.objectKey) && it.ownerUserId == ownerUserId }
            ?: return null
        return "data:${photo.contentType};base64," + Base64.getEncoder().encodeToString(storagePort.download(photo.objectKey))
    }

    private fun requireDownloadable(category: FileCategory, requester: Requester, ownerUserId: Long?) {
        if (!category.canDownload(requester, ownerUserId)) throw DocumentAccessDeniedException()
    }

    private fun resolveExtension(command: UploadFileCommand): FileExtension {
        val extension = FileExtension.fromFileName(command.originalName)
            ?: throw InvalidFileFormatException(command.originalName, command.category)
        if (!command.category.supports(extension)) {
            throw InvalidFileFormatException(command.originalName, command.category)
        }
        return extension
    }

    private fun deleteOrphan(objectKey: String) {
        runCatching { storagePort.delete(objectKey) }
            .onFailure { log.warn("Failed to delete orphaned object: {}", objectKey, it) }
    }
}
