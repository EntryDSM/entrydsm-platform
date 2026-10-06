package hs.kr.entrydsm.application.administration.adapterout.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

private const val MAX_FILE_BYTES = 64 * 1024 * 1024

/** Configuration의 범용 파일 RPC 연결. 64MiB 파일과 프로토콜 오버헤드를 수용한다. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class ConfigurationGrpcChannel(
    @Value("\${configuration.grpc.host}") host: String,
    @Value("\${configuration.grpc.port}") port: Int,
    @Value("\${configuration.grpc.deadline-ms}") val deadlineMs: Long,
) : DisposableBean {
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .maxInboundMessageSize(MAX_FILE_BYTES + 65536)
        .build()

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
