package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationFormResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ApplicationGrpcServiceTest {
    private lateinit var port: FakeApplicationPort
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: ApplicationServiceGrpc.ApplicationServiceBlockingStub

    @Before
    fun setUp() {
        port = FakeApplicationPort()
        server = ServerBuilder.forPort(0).addService(ApplicationGrpcService(port)).build().start()
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port).usePlaintext().build()
        stub = ApplicationServiceGrpc.newBlockingStub(channel)
    }

    @After
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun servesCreateGetAndCancelContract() {
        val created = stub.createApplication(CreateApplicationRequest.newBuilder().setUserId(USER_ID).build())
        val found = stub.getApplication(GetApplicationRequest.newBuilder().setUserId(USER_ID).build())
        val canceled = stub.cancelApplication(
            CancelApplicationRequest.newBuilder().setUserId(USER_ID).setReason("개인 사유").build(),
        )

        assertEquals(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_DRAFT, created.applicantStatus)
        assertEquals(hs.kr.entrydsm.application.grpc.PassStatus.PASS_STATUS_NOT_ANNOUNCED, found.passStatus)
        assertFalse(found.hasSubmittedAtEpochMillis())
        assertEquals(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_CANCELED, canceled.applicantStatus)
        assertEquals("개인 사유", port.cancelReason)
        assertEquals(2, port.findCount)
    }

    @Test
    fun mapsInvalidAndMissingUsers() {
        val invalid = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplication(GetApplicationRequest.newBuilder().setUserId(0).build())
        }
        val missing = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplication(GetApplicationRequest.newBuilder().setUserId(404).build())
        }

        assertEquals(Status.Code.INVALID_ARGUMENT, invalid.status.code)
        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
    }

    @Test
    fun servesApplicantWithUnsetFieldsLeftEmpty() {
        port.applicant = ApplicantResult(
            applicantId = APPLICANT_ID,
            accountId = USER_ID,
            name = "홍길동",
            schoolName = null,
            region = Region.DAEJEON,
            admissionType = null,
            photoFileId = "photo_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8",
        )

        val found = stub.getApplicant(GetApplicantRequest.newBuilder().setApplicantId(APPLICANT_ID).build())
        val missing = assertThrows(StatusRuntimeException::class.java) {
            stub.getApplicant(GetApplicantRequest.newBuilder().setApplicantId(404).build())
        }

        assertEquals(APPLICANT_ID, found.applicantId)
        assertEquals(USER_ID, found.userId)
        assertEquals("홍길동", found.name)
        assertFalse(found.hasSchoolName())
        assertEquals(GrpcRegion.REGION_DAEJEON, found.region)
        assertEquals(GrpcAdmissionType.ADMISSION_TYPE_UNSPECIFIED, found.admissionType)
        assertEquals("photo_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8", found.photoFileId)
        assertEquals(Status.Code.NOT_FOUND, missing.status.code)
    }

    private class FakeApplicationPort : ApplicationPort {
        private var snapshot: ApplicationSnapshotResult? = null
        var applicant: ApplicantResult? = null
        var cancelReason: String? = null
        var findCount = 0

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            snapshot = snapshot(command.accountId ?: error("accountId is required"), ApplicantStatus.DRAFT)
            return CreateApplicantResult(1L, requireNotNull(snapshot))
        }

        override fun findByAccountId(accountId: Long): ApplicationSnapshotResult? {
            findCount += 1
            return snapshot?.takeIf { it.accountId == accountId }
        }

        override fun findApplicant(applicantId: Long): ApplicantResult? =
            applicant?.takeIf { it.applicantId == applicantId }

        override fun findApplicationForm(accountId: Long): ApplicationFormResult? = null

        override fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult {
            cancelReason = reason
            return snapshot(accountId, ApplicantStatus.CANCELED).also { snapshot = it }
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(null)

        private fun snapshot(accountId: Long, status: ApplicantStatus) = ApplicationSnapshotResult(
            accountId = accountId,
            applicantStatus = status,
            submittedAt = null,
            updatedAt = LocalDateTime.of(2026, 9, 9, 0, 0),
            passStatus = PassResultStatus.PENDING,
            announcedAt = null,
        )
    }

    private companion object {
        const val USER_ID = 10L
        const val APPLICANT_ID = 3L
    }
}
