package hs.kr.entrydsm.identity.adapterin.grpc

import hs.kr.entrydsm.identity.application.port.`in`.AccountPort
import hs.kr.entrydsm.identity.application.port.`in`.SensitiveAgreeResult
import hs.kr.entrydsm.identity.application.port.`in`.command.DeleteAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.command.ReadAccountCommand
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneRequest
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneResponse
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityGrpcServiceTest {
    @Test
    fun validatesRequestsAndMapsAccountFailuresWithoutPrivateDetails() {
        val service = IdentityGrpcService(object : AccountPort {
            override fun validateApplicationPhone(command: ReadAccountCommand, phoneNumber: String): Boolean = when (command.userId) {
                10L -> true
                11L -> false
                404L -> throw IdentityDomainException(ErrorCode.AUTH_UNAUTHORIZED)
                else -> error("private-account-details")
            }
            override fun deleteAccount(command: DeleteAccountCommand) = error("not used")
            override fun getBasicInfo(command: ReadAccountCommand): BasicInfoResult = error("not used")
            override fun getAuthority(command: ReadAccountCommand): UserSummaryResult = error("not used")
            override fun agreeSensitiveInformation(command: ReadAccountCommand): SensitiveAgreeResult = error("not used")
        })
        val cases = listOf(
            Triple(10L, "01012345678", Status.Code.OK),
            Triple(11L, "01012345678", Status.Code.OK),
            Triple(0L, "01012345678", Status.Code.INVALID_ARGUMENT),
            Triple(10L, "not-a-phone", Status.Code.INVALID_ARGUMENT),
            Triple(404L, "01012345678", Status.Code.NOT_FOUND),
            Triple(500L, "01012345678", Status.Code.INTERNAL),
        )
        for ((id, phone, expectedStatus) in cases) {
            var response: ValidateApplicationPhoneResponse? = null
            var failure: Throwable? = null
            var completed = false
            service.validateApplicationPhone(ValidateApplicationPhoneRequest.newBuilder().setAccountId(id).setPhoneNumber(phone).build(),
                object : StreamObserver<ValidateApplicationPhoneResponse> {
                    override fun onNext(value: ValidateApplicationPhoneResponse) { response = value }
                    override fun onError(error: Throwable) { failure = error }
                    override fun onCompleted() { completed = true }
                })
            assertEquals(expectedStatus, failure?.let { Status.fromThrowable(it).code } ?: Status.Code.OK)
            if (expectedStatus == Status.Code.OK) {
                assertEquals(id == 10L, response?.valid)
                assertEquals(true, completed)
            } else {
                assertNull(response)
                assertEquals(false, completed)
                assertNull(Status.fromThrowable(requireNotNull(failure)).description)
            }
        }
    }
}
