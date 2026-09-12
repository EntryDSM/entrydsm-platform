package hs.kr.entrydsm.notification.adapterin.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class NotificationGrpcServer(
    @Value("\${grpc.port:9090}") private val port: Int,
    private val notificationGrpcService: NotificationGrpcService,
) : SmartLifecycle {

    private var server: Server? = null
    private var running = false

    override fun start() {
        server = ServerBuilder.forPort(port)
            .addService(notificationGrpcService)
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
