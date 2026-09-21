package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
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
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicationFormPdfPort
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import javax.imageio.ImageIO

class FileDocumentService(
    private val storagePort: StoragePort,
    private val fileDocumentRepository: FileDocumentRepository,
    private val presignExpirySeconds: Long,
    private val applicantPort: ApplicantPort,
    private val pdfRenderPort: PdfRenderPort,
    private val applicationFormPdfPort: ApplicationFormPdfPort,
    private val admissionYear: Int,
) : ApplicantFileUseCase,
    FileUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun generateApplicationForm(requester: Requester): DownloadableFile =
        renderApplicationForm(requireApplicationForm(requester))

    override fun generateApplicationForm(applicantId: Long, requester: Requester): DownloadableFile {
        val applicant = requireApplicant(applicantId, requester, FileCategory.APPLICATION::canDownload)
        return renderApplicationForm(
            applicantPort.findApplicationForm(applicant.userId) ?: throw ApplicantNotFoundException(applicantId),
        )
    }

    private fun renderApplicationForm(form: ApplicationForm): DownloadableFile {
        val category = FileCategory.APPLICATION
        val pdf = applicationFormPdfPort.render(
            form,
            photo = form.photoFileId?.let { findPhoto(it, form.userId) }?.let { storagePort.download(it.objectKey) },
        )
        val fileName = FileNaming.applicationFileName(form.applicantId)
        return store(category, fileName, fileName, FileExtension.PDF, pdf.size.toLong(), pdf.inputStream(), form.userId)
    }

    override fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile {
        val category = FileCategory.ADMISSION_TICKET
        val applicant = requireApplicant(applicantId, requester, category::canDownload)
        val pdf = renderTicket(applicantId, applicant, examineeNumber = null)
        val fileName = FileNaming.admissionTicketFileName(applicantId)
        return store(category, fileName, fileName, FileExtension.PDF, pdf.size.toLong(), pdf.inputStream(), applicant.userId)
    }

    override fun renderAdmissionTicket(applicantId: Long, examineeNumber: String?): ByteArray =
        renderTicket(
            applicantId,
            applicantPort.findById(applicantId) ?: throw ApplicantNotFoundException(applicantId),
            examineeNumber,
        )

    private fun renderTicket(applicantId: Long, applicant: Applicant, examineeNumber: String?): ByteArray =
        pdfRenderPort.render(
            AdmissionTicketHtml.render(
                admissionYear, applicantId, applicant, examineeNumber,
                photoDataUri = applicant.photoFileId?.let { photoDataUri(it, applicant.userId) },
            )
        )

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
        return store(category, fileName, command.originalName, extension, command.sizeBytes, content, command.requester.studentId)
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

    /**
     * 요청자 계정으로 찾으니 원서 주인이 곧 요청자다. 남의 원서를 훑을 수 없는 조회라 원서가 없으면 404 다.
     * 관리자도 권한은 통과하지만 관리자 계정에는 원서가 없어 404 다 — 관리자는 applicant id 를 받는 쪽을 쓴다.
     */
    private fun requireApplicationForm(requester: Requester): ApplicationForm {
        if (!FileCategory.APPLICATION.canDownload(requester, requester.userId)) throw DocumentAccessDeniedException()
        return applicantPort.findApplicationForm(requester.userId) ?: throw ApplicantNotFoundException(requester.userId, "accountId")
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

    /**
     * 서명 URL 을 올리기 전에 만든다. 서명은 객체가 없어도 되므로, 서명이 실패하면 아무것도 저장되지 않은 채로 끝난다.
     * 저장한 뒤에 서명하면 실패 응답을 받은 클라이언트가 다시 올려 같은 파일이 쌓인다.
     */
    private fun store(
        category: FileCategory,
        fileName: String,
        originalName: String,
        extension: FileExtension,
        sizeBytes: Long,
        content: InputStream,
        ownerUserId: Long?,
    ): DownloadableFile {
        val objectKey = category.objectKeyOf(fileName)
        val downloadUrl = storagePort.issueDownloadUrl(objectKey, presignExpirySeconds)
        val stored = storagePort.upload(objectKey, extension.contentType, sizeBytes, content)
        val document = FileDocument(
            publicId = FileNaming.publicId(category),
            originalName = originalName,
            objectKey = stored.objectKey,
            bucket = stored.bucket,
            contentType = extension.contentType,
            sizeBytes = sizeBytes,
            checksum = stored.checksum,
            ownerUserId = ownerUserId,
        )

        val saved = try {
            fileDocumentRepository.save(document)
        } catch (e: RuntimeException) {
            if (!category.keyedByApplicant) {
                // 요청마다 새 키라 이 요청만 쓴 객체다.
                deleteQuietly(objectKey)
                throw e
            }
            // 같은 지원자의 동시 요청이 같은 키에 올리고 행을 먼저 만들었을 수 있다. 객체를 지우면 그 행이 빈 객체를
            // 가리키므로 지우지 않고, 다시 저장해 그 행을 갱신한다. 다시 실패하면 남은 객체는 다음 적재가 덮어쓴다.
            fileDocumentRepository.save(document)
        }
        return DownloadableFile(saved, downloadUrl, presignExpirySeconds)
    }

    private fun downloadable(document: FileDocument) = DownloadableFile(
        document = document,
        downloadUrl = storagePort.issueDownloadUrl(document.objectKey, presignExpirySeconds),
        expiresIn = presignExpirySeconds,
    )

    private fun photoDataUri(photoFileId: String, ownerUserId: Long): String? =
        findPhoto(photoFileId, ownerUserId)?.let {
            val (contentType, bytes) = fitTicketPhoto(it.contentType, storagePort.download(it.objectKey))
            "data:$contentType;base64," + Base64.getEncoder().encodeToString(bytes)
        }

    /**
     * 원서에 적힌 사진 ID 는 학생이 보낸 값이라, 그 학생이 올린 사진일 때만 원서·수험표에 넣는다.
     *
     * ponytail: webp 사진은 openhtmltopdf·PDFBox(ImageIO)가 읽지 못해 빈 칸으로 찍힌다. 필요해지면 webp 디코더를 붙인다.
     *
     * ponytail: application V006 전에 숫자(`files.id`)로 저장된 사진 ID 도 찾는다. 본인 확인은 같아서 순번을 훑어도 남의 사진은
     * 못 넣는다. 운영 `applicants.photo_file_id` 가 모두 `photo_` 로 시작하게 되면 숫자 분기와 `findById` 를 지운다.
     */
    private fun findPhoto(photoFileId: String, ownerUserId: Long): FileDocument? {
        val found = when (val legacyId = photoFileId.toLongOrNull()) {
            null -> fileDocumentRepository.findByPublicId(photoFileId)
            else -> fileDocumentRepository.findById(legacyId)
        }
        return found?.takeIf { FileCategory.PHOTO.holds(it.objectKey) && it.ownerUserId == ownerUserId }
    }

    private fun deleteQuietly(objectKey: String) {
        runCatching { storagePort.delete(objectKey) }
            .onFailure { log.warn("Failed to delete stored object: {}", objectKey, it) }
    }
}

/** 원서·수험표는 지원자마다 저장 키가 하나다. 나머지는 요청마다 새 키(임의값)다. */
private val FileCategory.keyedByApplicant: Boolean
    get() = this == FileCategory.APPLICATION || this == FileCategory.ADMISSION_TICKET

/** 수험표 사진 칸(폭 약 66mm)에 약 230dpi 로 찍히는 폭. */
private const val TICKET_PHOTO_WIDTH = 600

/**
 * 증명사진을 수험표 사진 칸 크기로 줄여 JPEG 로 바꾼다. 관리자 일괄 출력은 수험표를 한 PDF 로 모으므로
 * 원본(최대 5MB)을 그대로 넣으면 파일이 인원수만큼 커진다. 칸보다 작거나 ImageIO 가 못 읽는 사진
 * (webp, CMYK JPEG)은 원본 그대로 둔다.
 *
 * ponytail: 원본을 통째로 디코딩한다(12MP 면 수십 MB). 동시 출력이 몰려 메모리가 모자라면 ImageReader 서브샘플링으로 읽는다.
 */
private fun fitTicketPhoto(contentType: String, bytes: ByteArray): Pair<String, ByteArray> {
    val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    if (image == null || image.width <= TICKET_PHOTO_WIDTH) return contentType to bytes

    // 한 번에 크게 줄이면 bilinear 가 픽셀을 건너뛰어 거칠어진다. 반씩 줄인다(getScaledInstance 보다 열 배 이상 빠르다).
    var fitted: BufferedImage = image
    while (fitted.width > TICKET_PHOTO_WIDTH) {
        val width = maxOf(fitted.width / 2, TICKET_PHOTO_WIDTH)
        val height = maxOf(fitted.height * width / fitted.width, 1)
        val source = fitted
        fitted = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { target ->
            target.createGraphics().run {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                // JPEG 에는 투명도가 없어 투명 PNG 는 흰 바탕에 얹는다.
                drawImage(source, 0, 0, width, height, Color.WHITE, null)
                dispose()
            }
        }
    }
    return "image/jpeg" to ByteArrayOutputStream().also { ImageIO.write(fitted, "jpg", it) }.toByteArray()
}
