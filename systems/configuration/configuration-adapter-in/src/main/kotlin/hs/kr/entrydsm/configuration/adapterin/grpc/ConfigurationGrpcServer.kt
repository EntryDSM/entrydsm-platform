package hs.kr.entrydsm.configuration.adapterin.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ConfigurationGrpcServer(
    @Value("\${grpc.port:9090}") private val port: Int,
    private val configurationGrpcService: ConfigurationGrpcService,
    private val fileStorageGrpcService: FileStorageGrpcService,
) : SmartLifecycle {

    private var server: Server? = null
    private var running = false

    override fun start() {
        server = ServerBuilder.forPort(port)
            .maxInboundMessageSize(hs.kr.entrydsm.configuration.domain.document.port.out.MAX_FILE_TRANSFER_BYTES + 65536)
            .addService(configurationGrpcService)
            .addService(fileStorageGrpcService)
            .build()
            .start()
        running = true
    }

    override fun stop() {
        server?.shutdown()?.awaitTermination(30, TimeUnit.SECONDS)
        running = false
    }

    override fun isRunning(): Boolean = running
}
