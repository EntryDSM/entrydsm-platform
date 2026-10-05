package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.Assert.assertEquals
import org.junit.Test

class GrpcAdministrationAdapterTest {
    @Test
    fun administrationReadsQuotaAndPreservesSourceFailure() {
        val quota = hs.kr.entrydsm.admin.domain.model.AdmissionQuota(
            hs.kr.entrydsm.admin.domain.enum.AdmissionType.entries.associateWith { 1 }, java.time.Instant.EPOCH, "10",
        )
        val mapper = tools.jackson.databind.json.JsonMapper.builder()
            .addModule(tools.jackson.module.kotlin.KotlinModule.Builder().build()).build()
        val upstream = object : hs.kr.entrydsm.application.grpc.AdministrationServiceGrpc.AdministrationServiceImplBase() {
            override fun readAdmissionQuota(request: hs.kr.entrydsm.application.grpc.AdministrationRequest,
                observer: StreamObserver<hs.kr.entrydsm.application.grpc.AdministrationResponse>) {
                assertEquals("10", request.userId)
                assertEquals("ADMIN", request.userRole)
                observer.onNext(hs.kr.entrydsm.application.grpc.AdministrationResponse.newBuilder()
                    .setDataJson(mapper.writeValueAsString(quota)).build())
                observer.onCompleted()
            }
            override fun readApplicant(request: hs.kr.entrydsm.application.grpc.AdministrationRequest,
                observer: StreamObserver<hs.kr.entrydsm.application.grpc.AdministrationResponse>) {
                observer.onNext(hs.kr.entrydsm.application.grpc.AdministrationResponse.newBuilder()
                    .setErrorCode("APPLICATION_SCORE_INVALID").setFailedCount(1).setTotalCount(85)
                    .addTargetIds(5).build())
                observer.onCompleted()
            }
        }
        val server = ServerBuilder.forPort(0).addService(upstream).build().start()
        val channel = hs.kr.entrydsm.admin.adapterout.grpc.ApplicationGrpcChannel("localhost", server.port, 30000, 30000)
        val adapter = hs.kr.entrydsm.admin.adapterout.grpc.GrpcAdministrationAdapter(channel)
        try {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes()
            assertEquals(ErrorCode.AUTH_UNAUTHORIZED,
                org.junit.Assert.assertThrows(AdminDomainException::class.java) { adapter.readQuota() }.errorCode)
            val request = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader,
                arrayOf(jakarta.servlet.http.HttpServletRequest::class.java)) { _, method, args ->
                if (method.name == "getHeader") when (args[0]) {
                    "X-User-Id" -> "10"
                    "X-User-Role" -> "ADMIN"
                    else -> null
                } else null
            } as jakarta.servlet.http.HttpServletRequest
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                org.springframework.web.context.request.ServletRequestAttributes(request))
            assertEquals(quota, adapter.readQuota())
            val error = org.junit.Assert.assertThrows(AdminDomainException::class.java) { adapter.findDetail(5) }
            assertEquals(ErrorCode.APPLICATION_SCORE_INVALID, error.errorCode)
            assertEquals(1, error.failedCount)
            assertEquals(85, error.totalCount)
            assertEquals(listOf(5L), error.targetIds)
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes()
            channel.destroy()
            server.shutdownNow()
        }
    }

}
