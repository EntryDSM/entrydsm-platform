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
) : SmartLifecycle {

    private var server: Server? = null
    private var running = false

    override fun start() {
        server = ServerBuilder.forPort(port)
            .addService(configurationGrpcService)
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
