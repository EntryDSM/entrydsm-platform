package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import java.time.Clock
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** `export_job`의 미완료 행을 durable outbox로 사용합니다. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class ExportJobDispatcher(
    private val repository: ExportJobRepository,
    private val processor: ExportJobProcessor,
    private val clock: Clock,
    @Value("\${admin.export.claim-timeout-ms:1200000}")
    private val claimTimeoutMs: Long,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${admin.export.poll-delay-ms:1000}")
    fun dispatch() {
        val now = Instant.now(clock)
        val job = repository.claimNext(now, now.minusMillis(claimTimeoutMs)) ?: return
        runCatching {
            processor.processAsync(job)
        }.onFailure { cause ->
            repository.save(job.pending())
            logger.warn("Export dispatch deferred [exportJobId={}]", job.exportJobId, cause)
        }
    }
}
