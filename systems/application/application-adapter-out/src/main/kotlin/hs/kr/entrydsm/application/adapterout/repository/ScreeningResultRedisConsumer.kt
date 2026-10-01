package hs.kr.entrydsm.application.adapterout.repository

import hs.kr.entrydsm.application.grpc.ScreeningResultChangedEvent
import io.lettuce.core.RedisBusyException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.RedisSystemException
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.Base64

@Component
class ScreeningResultRedisConsumer(
    private val redis: StringRedisTemplate,
    private val handler: ScreeningResultEventHandler,
    @Value("\${admin.events.screening-result-stream:admin.screening-result}") private val stream: String,
    @Value("\${admin.events.consumer-group:application-screening-result}") private val group: String,
    @Value("\${admin.events.consumer-name:application-screening-result}") private val consumerName: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${admin.events.poll-delay-ms:1000}")
    fun poll() {
        val ops = redis.opsForStream<String, String>()
        try {
            // 빈 스트림도 생성하여 관리자 첫 이벤트 이전에 구독할 수 있다.
            redis.execute(org.springframework.data.redis.core.RedisCallback<Any> { connection ->
                connection.streamCommands().xGroupCreate(stream.toByteArray(), group, ReadOffset.from("0-0"), true)
            })
        } catch (exception: RedisSystemException) {
            if (exception.cause !is RedisBusyException) throw exception
        }
        for (offset in listOf(ReadOffset.from("0"), ReadOffset.lastConsumed())) {
            ops.read(Consumer.from(group, consumerName), StreamReadOptions.empty().count(100), StreamOffset.create(stream, offset))
                .orEmpty().forEach { record ->
                    // ponytail: 실패 이벤트는 다음 poll에 재시도한다. 쌓이면 전달 횟수 상한과 dead-letter를 둔다.
                    try {
                        handler.consume(ScreeningResultChangedEvent.parseFrom(Base64.getDecoder().decode(record.value.getValue("payload"))))
                        ops.acknowledge(stream, group, record.id)
                    } catch (exception: Exception) {
                        logger.error("합격 결과 이벤트 수신 실패 [recordId={}]", record.id, exception)
                    }
                }
        }
    }
}
