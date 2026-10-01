package hs.kr.entrydsm.admin.adapterout.persistence

import com.google.protobuf.InvalidProtocolBufferException
import hs.kr.entrydsm.application.grpc.ApplicantStatusChangedEvent
import java.time.Duration
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ApplicantExportEventConsumer(
    private val redis: StringRedisTemplate,
    private val store: ApplicantProjectionStore,
    private val metrics: io.micrometer.core.instrument.MeterRegistry,
    @Value("\${entrydsm.application.events.applicant-status-stream:application.applicant-status}") private val stream: String,
) {
    private val group = "admin-applicant-export"
    private val consumer = UUID.randomUUID().toString()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${admin.export-event.poll-delay-ms:1000}")
    fun receive() {
        val ops = redis.opsForStream<String, String>()
        try {
            try { ops.createGroup(stream, ReadOffset.from("0-0"), group) } catch (exception: Exception) {
                if (generateSequence(exception as Throwable?) { it.cause }.none { it.message?.contains("BUSYGROUP") == true }) throw exception
            }
            val pending = ops.pending(stream, group, Range.unbounded<String>(), 100, Duration.ofSeconds(30)).toList()
            val ids = pending.map { it.id }.toTypedArray()
            val claimed = if (ids.isEmpty()) emptyList() else ops.claim(stream, group, consumer, Duration.ofSeconds(30), *ids)
            val fresh = ops.read(Consumer.from(group, consumer), StreamReadOptions.empty().count(100),
                StreamOffset.create(stream, ReadOffset.lastConsumed())).orEmpty()
            (claimed + fresh).forEach { record ->
                try {
                    val payload = requireNotNull(record.value["payload"])
                    val event = ApplicantStatusChangedEvent.parseFrom(Base64.getDecoder().decode(payload))
                    if (event.applicantId > 0) {
                        store.apply(event)
                        val lag = (System.currentTimeMillis() - event.occurredAtEpochMillis).coerceAtLeast(0)
                        metrics.timer("admin.applicant.projection.lag").record(lag, java.util.concurrent.TimeUnit.MILLISECONDS)
                        if (lag > 30000) logger.warn("Applicant projection delayed [recordId={}, lagMs={}]", record.id, lag)
                    } // store의 트랜잭션 commit 후에만 ACK한다.
                    ops.acknowledge(stream, group, record.id)
                    metrics.counter("admin.applicant.projection.events", "result", "success").increment()
                } catch (exception: Exception) {
                    metrics.counter("admin.applicant.projection.events", "result", "failure").increment()
                    logger.error("Applicant projection event failed [recordId={}]", record.id, exception)
                    if (exception is IllegalArgumentException || exception is InvalidProtocolBufferException ||
                        (pending.find { it.id == record.id }?.totalDeliveryCount ?: 0) >= 5) {
                        // 개인정보를 복제하지 않는다. 원본 recordId만 남기고 정기 대사로 복구한다.
                        ops.add("$stream.admin-failed", mapOf("recordId" to record.id.value))
                        ops.acknowledge(stream, group, record.id)
                        metrics.counter("admin.applicant.projection.events", "result", "quarantined").increment()
                        ops.trim("$stream.admin-failed", 1000)
                        logger.error("Applicant projection event quarantined [recordId={}]", record.id)
                    }
                }
            }
        } catch (exception: Exception) {
            logger.error("Applicant projection consumer unavailable", exception)
        }
    }
}
