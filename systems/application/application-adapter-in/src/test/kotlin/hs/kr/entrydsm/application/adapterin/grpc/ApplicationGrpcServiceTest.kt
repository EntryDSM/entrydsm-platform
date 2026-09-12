package hs.kr.entrydsm.application.adapterin.grpc

import hs.kr.entrydsm.application.application.port.`in`.ApplicantQueryPort
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.PassResultPort
import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantScoreResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantSummaryResult
import hs.kr.entrydsm.application.application.port.`in`.result.ApplicationSnapshotResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.ResultType
import hs.kr.entrydsm.application.domain.model.PassResult
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsRequest
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.CancelApplicationRequest
import hs.kr.entrydsm.application.grpc.CreateApplicationRequest
import hs.kr.entrydsm.application.grpc.GetApplicationRequest
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.PassResultEntry
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ApplicationGrpcServiceTest {
    private lateinit var port: FakeApplicationPort
    private lateinit var passResultPort: FakePassResultPort
    private lateinit var applicantQueryPort: FakeApplicantQueryPort
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: ApplicationServiceGrpc.ApplicationServiceBlockingStub

    @Before
    fun setUp() {
        port = FakeApplicationPort()
        passResultPort = FakePassResultPort()
        applicantQueryPort = FakeApplicantQueryPort()
        server = ServerBuilder.forPort(0).addService(ApplicationGrpcService(port, passResultPort, applicantQueryPort)).build().start()
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
    fun announcesPassResults() {
        val response = stub.announcePassResults(
            AnnouncePassResultsRequest.newBuilder()
                .addEntries(passEntry(USER_ID, hs.kr.entrydsm.application.grpc.ResultType.RESULT_TYPE_DOCUMENT))
                .addEntries(passEntry(USER_ID + 1, hs.kr.entrydsm.application.grpc.ResultType.RESULT_TYPE_FINAL))
                .build(),
        )

        assertEquals(2, response.appliedCount)
        assertEquals(
            listOf(ResultType.DOCUMENT, ResultType.FINAL),
            passResultPort.announced.map { it.resultType },
        )
        assertEquals(PassResultStatus.PASS, passResultPort.announced.first().result)
        assertEquals(ADMIN_ID, passResultPort.announced.first().processedBy)
    }

    @Test
    fun rejectsUnannouncedPassResult() {
        val rejected = assertThrows(StatusRuntimeException::class.java) {
            stub.announcePassResults(
                AnnouncePassResultsRequest.newBuilder()
                    .addEntries(
                        PassResultEntry.newBuilder()
                            .setApplicantId(USER_ID)
                            .setResultType(hs.kr.entrydsm.application.grpc.ResultType.RESULT_TYPE_FINAL)
                            .setResult(hs.kr.entrydsm.application.grpc.PassStatus.PASS_STATUS_NOT_ANNOUNCED)
                            .setProcessedAtEpochMillis(0)
                            .build(),
                    )
                    .build(),
            )
        }

        assertEquals(Status.Code.INVALID_ARGUMENT, rejected.status.code)
        assertEquals(0, passResultPort.announced.size)
    }

    private fun passEntry(
        applicantId: Long,
        resultType: hs.kr.entrydsm.application.grpc.ResultType,
    ): PassResultEntry = PassResultEntry.newBuilder()
        .setApplicantId(applicantId)
        .setResultType(resultType)
        .setResult(hs.kr.entrydsm.application.grpc.PassStatus.PASS_STATUS_PASSED)
        .setProcessedBy(ADMIN_ID)
        .setProcessedAtEpochMillis(0)
        .build()

    @Test
    fun listsSubmittedApplicantsWithOptionalFields() {
        val items = stub.listApplicants(ListApplicantsRequest.getDefaultInstance()).itemsList

        val scored = items.single { it.applicantId == 1L }
        val unscored = items.single { it.applicantId == 2L }

        assertEquals(hs.kr.entrydsm.application.grpc.Region.REGION_DAEJEON, scored.region)
        assertEquals(
            hs.kr.entrydsm.application.grpc.AdmissionType.ADMISSION_TYPE_REGULAR,
            scored.admissionType,
        )
        assertEquals(
            hs.kr.entrydsm.application.grpc.GraduationType.GRADUATION_TYPE_PROSPECTIVE,
            scored.graduationType,
        )
        assertEquals("2010-03-02", scored.birthDate)
        assertEquals(96.5, scored.score.totalScore, 0.0001)
        assertEquals(80.0, scored.score.subjectScore, 0.0001)

        // 성적 미산출과 미기재 항목은 값을 싣지 않아 admin 이 구분할 수 있어야 한다.
        assertFalse(unscored.hasScore())
        assertFalse(unscored.hasBirthDate())
        assertEquals(hs.kr.entrydsm.application.grpc.Region.REGION_UNSPECIFIED, unscored.region)
    }

    private class FakePassResultPort : PassResultPort {
        val announced = mutableListOf<PassResult>()

        override fun announce(command: AnnouncePassResultsCommand): Int {
            announced += command.results
            return command.results.size
        }
    }

    private class FakeApplicantQueryPort : ApplicantQueryPort {
        override fun findAllSubmitted(): List<ApplicantSummaryResult> = listOf(
            ApplicantSummaryResult(
                applicantId = 1L,
                userId = USER_ID,
                name = "김대덕",
                birthdate = LocalDate.of(2010, 3, 2),
                phoneNumber = "01012345678",
                region = Region.DAEJEON,
                admissionType = AdmissionType.REGULAR,
                graduationType = GraduationType.PROSPECTIVE,
                schoolName = "대덕중학교",
                applicantStatus = ApplicantStatus.SUBMITTED,
                submittedAt = LocalDateTime.of(2026, 10, 1, 12, 0),
                score = ApplicantScoreResult(80.0, 15.0, 15.0, 96.5),
                updatedAt = LocalDateTime.of(2026, 10, 1, 12, 0),
            ),
            ApplicantSummaryResult(
                applicantId = 2L,
                userId = USER_ID + 1,
                name = "이한빛",
                birthdate = null,
                phoneNumber = "",
                region = null,
                admissionType = null,
                graduationType = null,
                schoolName = "",
                applicantStatus = ApplicantStatus.SUBMITTED,
                submittedAt = null,
                score = null,
                updatedAt = LocalDateTime.of(2026, 10, 2, 12, 0),
            ),
        )
    }

    private class FakeApplicationPort : ApplicationPort {
        private var snapshot: ApplicationSnapshotResult? = null
        var cancelReason: String? = null
        var findCount = 0

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            snapshot = snapshot(command.userId ?: error("userId is required"), ApplicantStatus.DRAFT)
            return CreateApplicantResult(1L, requireNotNull(snapshot))
        }

        override fun findByUserId(userId: Long): ApplicationSnapshotResult? {
            findCount += 1
            return snapshot?.takeIf { it.userId == userId }
        }

        override fun cancel(userId: Long, reason: String?): ApplicationSnapshotResult {
            cancelReason = reason
            return snapshot(userId, ApplicantStatus.CANCELED).also { snapshot = it }
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(null)

        private fun snapshot(userId: Long, status: ApplicantStatus) = ApplicationSnapshotResult(
            userId = userId,
            applicantStatus = status,
            submittedAt = null,
            updatedAt = LocalDateTime.of(2026, 9, 9, 0, 0),
            passStatus = PassResultStatus.PENDING,
            announcedAt = null,
        )
    }

    private companion object {
        const val USER_ID = 10L
        const val ADMIN_ID = 77L
    }
}
