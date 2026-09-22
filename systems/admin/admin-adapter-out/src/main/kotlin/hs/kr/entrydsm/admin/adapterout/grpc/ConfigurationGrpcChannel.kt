package hs.kr.entrydsm.admin.adapterout.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** 증명사진은 5MB 까지 올라온다. document 가 못 줄인 사진이 원본 그대로 든 수험표도 받는다. */
private const val MAX_TICKET_BYTES = 16 * 1024 * 1024

/**
 * configuration(document) 으로 나가는 gRPC 연결입니다.
 *
 * 수험표 한 장에 증명사진이 들어가 gRPC 기본 수신 한도(4MB)를 넘을 수 있어 한도를 올립니다.
 * document 가 큰 사진을 줄여 넣으므로 보통은 한 장에 수백 KB 입니다.
 */
@Component
class ConfigurationGrpcChannel(
    @Value("\${configuration.grpc.host}") host: String,
    @Value("\${configuration.grpc.port}") port: Int,
    @Value("\${configuration.grpc.deadline-ms}") val deadlineMs: Long,
) : DisposableBean {
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .maxInboundMessageSize(MAX_TICKET_BYTES)
        .build()

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
