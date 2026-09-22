package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.DownloadLink
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.`in`.CreateFirstPassFileUseCase
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.StoragePort
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class FirstPassFileService(
    private val exportJobRepository: ExportJobRepository,
    private val exportJobProcessor: ExportJobProcessor,
    private val storagePort: StoragePort,
    private val clock: Clock,
    @Value("\${admin.storage.download-url-expires-seconds:900}")
    private val expiresInSeconds: Long,
) : CreateFirstPassFileUseCase {
    override fun create(): DownloadLink {
        val job = exportJobRepository.save(
            ExportJob(
                exportJobId = "exp_" + UUID.randomUUID().toString().replace("-", ""),
                type = ExportType.FIRST_PASS_LIST,
                status = ExportStatus.PENDING,
                createdAt = Instant.now(clock),
            ),
        )
        exportJobProcessor.processNow(job)
        val objectKey = requireNotNull(exportJobRepository.findByExportJobId(job.exportJobId)?.objectKey)
        return DownloadLink(
            downloadUrl = storagePort.issueDownloadUrl(objectKey, expiresInSeconds),
            expiresAt = Instant.now(clock).plusSeconds(expiresInSeconds),
        )
    }
}
