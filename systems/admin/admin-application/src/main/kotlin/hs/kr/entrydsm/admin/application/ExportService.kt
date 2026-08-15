package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.DownloadLink
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.ExportJobView
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadExportUseCase
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val EXPORT_JOB_ID_PREFIX = "exp_"

/**
 * 내보내기 작업을 접수하고 상태를 조회합니다. 실제 생성은 [ExportJobProcessor]가 맡습니다.
 */
@Service
@Transactional(readOnly = true)
class ExportService(
    private val exportJobRepository: ExportJobRepository,
    private val exportJobProcessor: ExportJobProcessor,
    private val storagePort: StoragePort,
    private val clock: Clock,
    @Value("\${admin.storage.download-url-expires-seconds:900}")
    private val downloadUrlExpiresInSeconds: Long,
) : CreateExportUseCase,
    ReadExportUseCase {

    @Transactional
    override fun create(command: CreateExportCommand): ExportJob {
        val job = exportJobRepository.save(
            ExportJob(
                exportJobId = EXPORT_JOB_ID_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                type = command.type,
                status = ExportStatus.PENDING,
                filter = command.filter,
                createdAt = Instant.now(clock),
            ),
        )

        exportJobProcessor.process(job)
        return job
    }

    override fun findById(exportJobId: String): ExportJobView {
        val job = exportJobRepository.findByExportJobId(exportJobId)
            ?: throw AdminDomainException(ErrorCode.EXPORT_JOB_NOT_FOUND)

        val download = job.objectKey
            ?.takeIf { job.status.isDownloadable() }
            ?.let {
                DownloadLink(
                    downloadUrl = storagePort.issueDownloadUrl(it, downloadUrlExpiresInSeconds),
                    expiresAt = Instant.now(clock).plusSeconds(downloadUrlExpiresInSeconds),
                )
            }

        return ExportJobView(job = job, download = download)
    }
}
