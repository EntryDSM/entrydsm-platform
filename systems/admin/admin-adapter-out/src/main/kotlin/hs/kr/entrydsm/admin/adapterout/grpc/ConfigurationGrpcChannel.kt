package hs.kr.entrydsm.admin.adapterout.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 1차 합격자 전원의 수험표 xlsx 를 응답 하나로 받는다. document 가 사진을 줄여 한 장에 수십 KB 라 백여 명이면
 * 수 MB 다. 줄이지 못한 원본 사진(최대 5MB)이 몇 장 섞여도 받게 넉넉히 둔다.
 *
 * ponytail: 한도를 넘을 만큼 커지면 서버 스트리밍으로 나눠 받는다.
 */
private const val MAX_TICKET_BYTES = 64 * 1024 * 1024

/**
 * configuration(document) 으로 나가는 gRPC 연결입니다.
 *
 * 수험표 xlsx 에 증명사진이 인원수만큼 들어가 gRPC 기본 수신 한도(4MB)를 넘으므로 한도를 올립니다.
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
