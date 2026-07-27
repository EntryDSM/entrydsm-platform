package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.DownloadUrl
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileExtension
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.command.IssueDownloadUrlCommand
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.ReadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.`in`.UploadFileUseCase
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

@Service
@Transactional(readOnly = true)
class FileDocumentService(
    private val storagePort: StoragePort,
    private val fileDocumentRepository: FileDocumentRepository,
    @Value("\${aws.s3.presign-expiry-seconds:600}") private val presignExpirySeconds: Long,
) : UploadFileUseCase,
    IssueDownloadUrlUseCase,
    ReadFileUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun upload(command: UploadFileCommand, content: InputStream): FileDocument {
        val extension = resolveExtension(command)
        if (command.category.exceedsMaxSize(command.sizeBytes)) {
            throw FileTooLargeException(command.sizeBytes, command.category.maxSizeBytes)
        }

        val objectKey = command.category.objectKeyOf(FileNaming.requireSafeFileName(command.fileName))
        val stored = storagePort.upload(objectKey, extension.contentType, command.sizeBytes, content)

        return try {
            fileDocumentRepository.save(
                FileDocument(
                    originalName = command.originalName,
                    objectKey = stored.objectKey,
                    bucket = stored.bucket,
                    contentType = extension.contentType,
                    sizeBytes = command.sizeBytes,
                    checksum = stored.checksum,
                )
            )
        } catch (e: RuntimeException) {
            deleteOrphan(objectKey)
            throw e
        }
    }

    override fun issueByCommand(command: IssueDownloadUrlCommand): DownloadUrl {
        val fileName = FileNaming.requireSafeFileName(command.fileName)
        val objectKey = command.category.objectKeyOf(fileName)
        if (!storagePort.exists(objectKey)) throw FileDocumentNotFoundException(objectKey)
        return DownloadUrl(
            fileName = fileName,
            downloadUrl = storagePort.issueDownloadUrl(objectKey, presignExpirySeconds),
            expiresIn = presignExpirySeconds,
        )
    }

    override fun issueById(id: Long): DownloadUrl {
        val fileDocument = findById(id)
        return DownloadUrl(
            fileName = fileDocument.originalName,
            downloadUrl = storagePort.issueDownloadUrl(fileDocument.objectKey, presignExpirySeconds),
            expiresIn = presignExpirySeconds,
        )
    }

    override fun findById(id: Long): FileDocument =
        fileDocumentRepository.findById(id) ?: throw FileDocumentNotFoundException("id=$id")

    override fun findByFileName(category: FileCategory, fileName: String): FileDocument? =
        fileDocumentRepository.findByObjectKey(
            category.objectKeyOf(FileNaming.requireSafeFileName(fileName))
        )

    override fun existsById(id: Long): Boolean =
        fileDocumentRepository.existsById(id)

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
