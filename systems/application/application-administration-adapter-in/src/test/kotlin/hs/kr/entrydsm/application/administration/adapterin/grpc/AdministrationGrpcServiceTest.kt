package hs.kr.entrydsm.application.administration.adapterin.grpc

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.`in`.*
import hs.kr.entrydsm.application.grpc.AdministrationRequest
import hs.kr.entrydsm.application.grpc.AdministrationResponse
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AdministrationGrpcServiceTest {
    private var calls = 0
    private val service = AdministrationGrpcService(
        port(ReadApplicantUseCase::class.java), port(UpdateApplicantUseCase::class.java),
        port(IssueExamineeNumberUseCase::class.java), port(DeleteApplicantUseCase::class.java),
        port(ReadScorePolicyUseCase::class.java), port(UpdateScorePolicyUseCase::class.java),
        port(ReadAdmissionQuotaUseCase::class.java), port(UpdateAdmissionQuotaUseCase::class.java),
        port(EvaluateFirstScreeningUseCase::class.java), port(EvaluateFinalScreeningUseCase::class.java),
        port(ReadStatisticsUseCase::class.java), port(CreateExportUseCase::class.java), port(ReadExportUseCase::class.java),
    )

    @Test
    fun missingIdentityAndNonAdminCannotExecuteCommands() {
        assertEquals("AUTH_UNAUTHORIZED", invoke("", "ADMIN", "{\"applicantId\":5}").errorCode)
        assertEquals("ACCESS_DENIED", invoke("10", "USER", "{\"applicantId\":5}").errorCode)
        assertEquals(0, calls)
    }

    @Test
    fun invalidJsonAndNonpositiveIdCannotExecuteCommands() {
        assertEquals("INVALID_REQUEST_BODY", invoke("10", "ADMIN", "{").errorCode)
        assertEquals("INVALID_REQUEST_BODY", invoke("10", "ADMIN", "{\"applicantId\":0}").errorCode)
        assertEquals(0, calls)
    }

    @Test
    fun scoreFailurePreservesCodeAndCountsWithoutExceptionMessage() {
        val result = invoke("10", "ADMIN", "{\"applicantId\":5}")
        assertEquals(1, calls)
        assertEquals("APPLICATION_SCORE_INVALID", result.errorCode)
        assertEquals(1, result.failedCount)
        assertEquals(85, result.totalCount)
        assertEquals(listOf(5L), result.targetIdsList)
        assertEquals("", result.dataJson)
    }

    private fun invoke(userId: String, role: String, json: String): AdministrationResponse {
        var result: AdministrationResponse? = null
        var completed = false
        service.readApplicant(AdministrationRequest.newBuilder().setUserId(userId).setUserRole(role)
            .setCommandJson(json).build(), object : StreamObserver<AdministrationResponse> {
            override fun onNext(value: AdministrationResponse) { result = value }
            override fun onError(error: Throwable) { throw AssertionError(error) }
            override fun onCompleted() { completed = true }
        })
        assertTrue(completed)
        return requireNotNull(result)
    }

    private fun <T> port(type: Class<T>): T = type.cast(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, _, _ ->
            calls++
            throw AdminDomainException(ErrorCode.APPLICATION_SCORE_INVALID,
                IllegalArgumentException("원서 본문"), failedCount = 1, totalCount = 85, targetIds = listOf(5))
        },
    )
}
