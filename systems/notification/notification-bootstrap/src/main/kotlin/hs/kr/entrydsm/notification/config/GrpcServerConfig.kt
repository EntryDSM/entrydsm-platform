package hs.kr.entrydsm.notification.config

import hs.kr.entrydsm.notification.adapterin.grpc.NotificationGrpcService
import io.grpc.Server
import io.grpc.ServerBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration(proxyBeanMethods = false)
class GrpcServerConfig {
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @Lazy(false)
    fun grpcServer(
        @Value("\${grpc.port}") port: Int,
        notificationGrpcService: NotificationGrpcService,
    ): Server = ServerBuilder.forPort(port)
        .addService(notificationGrpcService)
        .build()
}
