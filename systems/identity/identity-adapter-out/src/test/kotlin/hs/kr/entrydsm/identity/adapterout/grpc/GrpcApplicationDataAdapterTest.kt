package hs.kr.entrydsm.identity.adapterout.grpc

import hs.kr.entrydsm.application.grpc.ApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.identity.domain.enum.ApplicantStatus as DomainApplicantStatus
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.enum.PassStatus as DomainPassStatus
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GrpcApplicationDataAdapterTest {
    private lateinit var service: FakeApplicationService
    private lateinit var server: Server
    private lateinit var adapter: GrpcApplicationDataAdapter

    @Before
    fun setUp() {
        service = FakeApplicationService()
        server = ServerBuilder.forPort(0).addService(service).build().start()
        adapter = GrpcApplicationDataAdapter("127.0.0.1", server.port, 10_000)
    }

    @After
    fun tearDown() {
        adapter.destroy()
        server.shutdownNow()
    }

    @Test
    fun connectsToApplicationGrpcContract() {
        val created = adapter.create(USER_ID, Instant.EPOCH)
        val found = requireNotNull(adapter.findByUserId(USER_ID))
        val canceled = adapter.cancel(USER_ID, "개인 사유", Instant.EPOCH)

        assertEquals(DomainApplicantStatus.DRAFT, created.applicantStatus)
        assertEquals(DomainApplicantStatus.SUBMITTED, found.applicantStatus)
        assertEquals(SUBMITTED_AT, found.submittedAt)
        assertEquals(DomainPassStatus.PASSED, found.passStatus)
        assertEquals(ANNOUNCED_AT, found.announcedAt)
        assertEquals(DomainApplicantStatus.CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", service.cancelReason)
    }

    @Test
    fun mapsGrpcErrors() {
        assertNull(adapter.findByUserId(NOT_FOUND_USER_ID))

        val exception = assertThrows(IdentityDomainException::class.java) {
            adapter.cancel(NOT_CANCELABLE_USER_ID, null, Instant.EPOCH)
        }
        assertEquals(ErrorCode.APPLICATION_CANCEL_NOT_ALLOWED, exception.errorCode)
    }

    private class FakeApplicationService : ApplicationServiceGrpc.ApplicationServiceImplBase() {
        var cancelReason: String? = null

        override fun createApplication(
            request: CreateApplicationRequest,
            responseObserver: StreamObserver<ApplicationResponse>,
        ) = responseObserver.respond(response(request.userId, ApplicantStatus.APPLICANT_STATUS_DRAFT))

        override fun getApplication(
            request: GetApplicationRequest,
            responseObserver: StreamObserver<ApplicationResponse>,
        ) {
            if (request.userId == NOT_FOUND_USER_ID) {
                responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
                return
            }
            responseObserver.respond(
                response(request.userId, ApplicantStatus.APPLICANT_STATUS_SUBMITTED)
                    .toBuilder()
                    .setSubmittedAtEpochMillis(SUBMITTED_AT.toEpochMilli())
                    .setPassStatus(PassStatus.PASS_STATUS_PASSED)
                    .setAnnouncedAtEpochMillis(ANNOUNCED_AT.toEpochMilli())
                    .build(),
            )
        }

        override fun cancelApplication(
            request: CancelApplicationRequest,
            responseObserver: StreamObserver<ApplicationResponse>,
        ) {
            if (request.userId == NOT_CANCELABLE_USER_ID) {
                responseObserver.onError(Status.FAILED_PRECONDITION.asRuntimeException())
                return
            }
            cancelReason = request.reason.takeIf { request.hasReason() }
            responseObserver.respond(response(request.userId, ApplicantStatus.APPLICANT_STATUS_CANCELED))
        }

        private fun response(userId: Long, status: ApplicantStatus): ApplicationResponse =
            ApplicationResponse.newBuilder()
                .setUserId(userId)
                .setApplicantStatus(status)
                .setUpdatedAtEpochMillis(UPDATED_AT.toEpochMilli())
                .setPassStatus(PassStatus.PASS_STATUS_NOT_ANNOUNCED)
                .build()

        private fun StreamObserver<ApplicationResponse>.respond(response: ApplicationResponse) {
            onNext(response)
            onCompleted()
        }
    }

    private companion object {
        const val USER_ID = 10L
        const val NOT_FOUND_USER_ID = 404L
        const val NOT_CANCELABLE_USER_ID = 409L
        val UPDATED_AT: Instant = Instant.parse("2026-09-09T00:00:00Z")
        val SUBMITTED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
        val ANNOUNCED_AT: Instant = Instant.parse("2026-09-08T00:00:00Z")
    }
}
