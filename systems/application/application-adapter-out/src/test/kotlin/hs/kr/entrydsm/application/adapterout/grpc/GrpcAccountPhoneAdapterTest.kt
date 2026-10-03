package hs.kr.entrydsm.application.adapterout.grpc

import hs.kr.entrydsm.application.application.exception.AccountPhoneLookupFailedException
import hs.kr.entrydsm.identity.grpc.IdentityServiceGrpc
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneRequest
import hs.kr.entrydsm.identity.grpc.ValidateApplicationPhoneResponse
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GrpcAccountPhoneAdapterTest {
    @Test
    fun sendsNormalizedPhoneAndReturnsValidationResult() {
        val requests = mutableListOf<ValidateApplicationPhoneRequest>()
        val service = object : IdentityServiceGrpc.IdentityServiceImplBase() {
            override fun validateApplicationPhone(request: ValidateApplicationPhoneRequest, observer: StreamObserver<ValidateApplicationPhoneResponse>) {
                requests += request
                observer.onNext(ValidateApplicationPhoneResponse.newBuilder().setValid(request.phoneNumber == "01012345678").build())
                observer.onCompleted()
            }
        }
        withAdapter(service) {
            assertEquals(true, it.validate(10L, "010-1234-5678"))
            assertEquals(false, it.validate(10L, "010-9999-8888"))
        }
        assertEquals(listOf(10L, 10L), requests.map { it.accountId })
        assertEquals(listOf("01012345678", "01099998888"), requests.map { it.phoneNumber })
    }

    @Test
    fun identityFailuresDoNotBypassPhoneValidation() {
        for (status in listOf(Status.NOT_FOUND, Status.UNAVAILABLE, Status.UNIMPLEMENTED, Status.DEADLINE_EXCEEDED)) {
            val service = object : IdentityServiceGrpc.IdentityServiceImplBase() {
                override fun validateApplicationPhone(request: ValidateApplicationPhoneRequest, observer: StreamObserver<ValidateApplicationPhoneResponse>) {
                    observer.onError(status.asRuntimeException())
                }
            }
            withAdapter(service) { adapter ->
                assertThrows(AccountPhoneLookupFailedException::class.java) { adapter.validate(10L, "010-1234-5678") }
            }
        }
    }

    private fun withAdapter(service: IdentityServiceGrpc.IdentityServiceImplBase, block: (GrpcAccountPhoneAdapter) -> Unit) {
        val server = ServerBuilder.forPort(0).addService(service).build().start()
        val adapter = GrpcAccountPhoneAdapter("localhost", server.port, 1000)
        try {
            block(adapter)
        } finally {
            adapter.destroy()
            server.shutdownNow()
        }
    }
}
