package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.FilePage
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.FileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.slf4j.LoggerFactory
import java.io.InputStream

class FileDocumentService(
    private val storagePort: StoragePort,
    private val fileDocumentRepository: FileDocumentRepository,
    private val presignExpirySeconds: Long,
    private val pdfIsReadable: (ByteArray) -> Boolean,
    private val storageEnvironment: String = "stag",
) : FileUseCase {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun upload(command: UploadFileCommand, content: InputStream): DownloadableFile {
        val category = command.category
        val extension = validate(command)
        if (!category.canStore(command.requester, ownerUserId = null)) throw DocumentAccessDeniedException()
        val fileName = when (category) {
            FileCategory.PHOTO -> FileNaming.photoFileName(extension)
            FileCategory.ATTACHMENT, FileCategory.GUIDELINE, FileCategory.REGISTRATION_DOCUMENT ->
                FileNaming.attachmentFileName(command.originalName)
            FileCategory.APPLICATION, FileCategory.ADMISSION_TICKET, FileCategory.REGISTRATION_FORM ->
                throw IllegalArgumentException("$category is stored per applicant")
        }
        val stored = if (category == FileCategory.REGISTRATION_DOCUMENT) {
            // 학생 요청마다 이 원본을 PDF 로 열어 채운다. 열리지 않는 원본은 여기서 거절해 관리자가 바로 알게 한다.
            content.readBytes().also {
                if (!pdfIsReadable(it)) throw InvalidFileFormatException(command.originalName, category)
            }.inputStream()
        } else {
            content
        }
        return store(category, fileName, command.originalName, extension, command.sizeBytes, stored, command.requester.studentId)
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
        val objectKey = category.objectKeyOf(fileName, storageEnvironment)
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
            deleteQuietly(objectKey)
            throw e
        }
        return DownloadableFile(saved, downloadUrl, presignExpirySeconds)
    }

    private fun downloadable(document: FileDocument) = DownloadableFile(
        document = document,
        downloadUrl = storagePort.issueDownloadUrl(document.objectKey, presignExpirySeconds),
        expiresIn = presignExpirySeconds,
    )

    private fun deleteQuietly(objectKey: String) {
        runCatching { storagePort.delete(objectKey) }
            .onFailure { log.warn("Failed to delete stored object: {}", objectKey, it) }
    }
}
