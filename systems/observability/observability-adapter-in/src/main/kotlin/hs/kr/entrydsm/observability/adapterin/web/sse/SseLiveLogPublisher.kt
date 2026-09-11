package hs.kr.entrydsm.observability.adapterin.web.sse

import hs.kr.entrydsm.observability.adapterin.web.dto.response.LiveLogEventResponse
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.LiveLogPublisherPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * 수집 요청을 받은 인스턴스와 SSE 구독자가 붙은 인스턴스가 다를 수 있어 로그 이벤트를 Redis Pub/Sub으로 모든 인스턴스에 보낸다.
 * 보낸 인스턴스 자신도 채널을 구독하므로 SSE 전송은 구독 쪽(LiveLogSubscriptionConfig)에서만 한다.
 */
@Component
class SseLiveLogPublisher(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : LiveLogPublisherPort {
    override fun publishClientLog(input: ClientLogInput) {
        val event = LiveLogEventResponse(
            kind = "CLIENT",
            level = input.level.name,
            source = input.source.name,
            message = input.message,
            pageUrl = input.pageUrl,
            occurredAt = input.occurredAt,
        )
        redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event))
    }

    companion object {
        const val CHANNEL = "monitor:live-log"
    }
}

@Configuration(proxyBeanMethods = false)
class LiveLogSubscriptionConfig {

    @Bean
    fun liveLogListenerContainer(
        connectionFactory: RedisConnectionFactory,
        sseBroadcaster: SseBroadcaster,
        objectMapper: ObjectMapper,
    ) = RedisMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
        addMessageListener(
            { message, _ -> sseBroadcaster.publishLog(objectMapper.readTree(message.body)) },
            ChannelTopic(SseLiveLogPublisher.CHANNEL),
        )
    }
}
