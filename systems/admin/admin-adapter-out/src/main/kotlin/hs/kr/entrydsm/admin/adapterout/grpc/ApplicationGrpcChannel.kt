package hs.kr.entrydsm.admin.adapterout.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * application 으로 나가는 gRPC 연결입니다.
 *
 * 지원자 목록은 회차 전체를 한 번에 받아 오므로 한 명 조회보다 시간이 더 걸립니다.
 * 제한 시간을 따로 둔 이유입니다.
 */
@Component
class ApplicationGrpcChannel(
    @Value("\${application.grpc.host}") host: String,
    @Value("\${application.grpc.port}") port: Int,
    @Value("\${application.grpc.deadline-ms}") val deadlineMs: Long,
    @Value("\${application.grpc.list-deadline-ms}") val listDeadlineMs: Long,
) : DisposableBean {
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build()

    override fun destroy() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
