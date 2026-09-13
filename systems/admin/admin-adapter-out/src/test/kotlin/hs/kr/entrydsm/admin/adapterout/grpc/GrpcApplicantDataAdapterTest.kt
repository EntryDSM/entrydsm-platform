package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.port.out.AnnouncedPassResult
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsRequest
import hs.kr.entrydsm.application.grpc.AnnouncePassResultsResponse
import hs.kr.entrydsm.application.grpc.ApplicantStatus as GrpcApplicantStatus
import hs.kr.entrydsm.application.grpc.ApplicantSummary
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.ListApplicantsResponse
import hs.kr.entrydsm.application.grpc.PassStatus as GrpcPassStatus
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.ResultType as GrpcResultType
import hs.kr.entrydsm.application.grpc.ScoreBreakdown as GrpcScoreBreakdown
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.stub.StreamObserver
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class GrpcApplicantDataAdapterTest {
    private lateinit var service: FakeApplicationService
    private lateinit var server: Server
    private lateinit var adapter: GrpcApplicantDataAdapter

    @Before
    fun setUp() {
        service = FakeApplicationService()
        server = ServerBuilder.forPort(0).addService(service).build().start()
        adapter = GrpcApplicantDataAdapter("127.0.0.1", server.port, 10_000)
    }

    @After
    fun tearDown() {
        adapter.destroy()
        server.shutdownNow()
    }

    @Test
    fun readsApplicantsFromApplication() {
        val records = adapter.findAllSubmitted()

        val complete = records.single { it.applicantId == 1L }
        assertEquals("김대덕", complete.name)
        assertEquals(LocalDate.of(2010, 3, 2), complete.birthDate)
        assertEquals(Region.DAEJEON, complete.region)
        assertEquals(AdmissionType.GENERAL, complete.admissionType)
        assertEquals(GraduationStatus.EXPECTED, complete.graduationStatus)
        assertEquals(96.5, complete.score?.totalScore ?: 0.0, 0.0001)
    }

    @Test
    fun leavesMissingApplicationFieldsEmpty() {
        val partial = adapter.findAllSubmitted().single { it.applicantId == 2L }

        assertNull(partial.birthDate)
        assertNull(partial.region)
        assertNull(partial.admissionType)
        assertNull(partial.graduationStatus)
        assertNull(partial.score)
    }

    @Test
    fun announcesScreeningResultsAsDocumentAndFinalStages() {
        val applied = adapter.announce(
            listOf(
                AnnouncedPassResult(1L, ApplicantStatus.FIRST_PASS),
                AnnouncedPassResult(2L, ApplicantStatus.FINAL_FAIL),
            ),
            PROCESSED_AT,
        )

        assertEquals(2, applied)
        val entries = service.lastAnnouncement?.entriesList.orEmpty()
        assertEquals(GrpcResultType.RESULT_TYPE_DOCUMENT, entries[0].resultType)
        assertEquals(GrpcPassStatus.PASS_STATUS_PASSED, entries[0].result)
        assertEquals(GrpcResultType.RESULT_TYPE_FINAL, entries[1].resultType)
        assertEquals(GrpcPassStatus.PASS_STATUS_FAILED, entries[1].result)
        assertEquals(PROCESSED_AT.toEpochMilli(), entries[0].processedAtEpochMillis)
    }

    @Test
    fun doesNotAnnouncePendingApplicants() {
        val applied = adapter.announce(listOf(AnnouncedPassResult(1L, ApplicantStatus.PENDING)), PROCESSED_AT)

        assertEquals(0, applied)
        assertNull(service.lastAnnouncement)
    }

    private class FakeApplicationService : ApplicationServiceGrpc.ApplicationServiceImplBase() {
        var lastAnnouncement: AnnouncePassResultsRequest? = null

        override fun listApplicants(
            request: ListApplicantsRequest,
            responseObserver: StreamObserver<ListApplicantsResponse>,
        ) {
            responseObserver.onNext(
                ListApplicantsResponse.newBuilder()
                    .addItems(
                        ApplicantSummary.newBuilder()
                            .setApplicantId(1L)
                            .setUserId(11L)
                            .setName("김대덕")
                            .setBirthDate("2010-03-02")
                            .setPhoneNumber("01012345678")
                            .setRegion(GrpcRegion.REGION_DAEJEON)
                            .setAdmissionType(GrpcAdmissionType.ADMISSION_TYPE_REGULAR)
                            .setGraduationType(GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE)
                            .setSchoolName("대덕중학교")
                            .setApplicantStatus(GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED)
                            .setScore(
                                GrpcScoreBreakdown.newBuilder()
                                    .setSubjectScore(80.0)
                                    .setAttendanceScore(15.0)
                                    .setVolunteerScore(15.0)
                                    .setTotalScore(96.5)
                                    .build(),
                            )
                            .setUpdatedAtEpochMillis(0)
                            .build(),
                    )
                    .addItems(
                        ApplicantSummary.newBuilder()
                            .setApplicantId(2L)
                            .setUserId(12L)
                            .setName("이한빛")
                            .setApplicantStatus(GrpcApplicantStatus.APPLICANT_STATUS_SUBMITTED)
                            .setUpdatedAtEpochMillis(0)
                            .build(),
                    )
                    .build(),
            )
            responseObserver.onCompleted()
        }

        override fun announcePassResults(
            request: AnnouncePassResultsRequest,
            responseObserver: StreamObserver<AnnouncePassResultsResponse>,
        ) {
            lastAnnouncement = request
            responseObserver.onNext(
                AnnouncePassResultsResponse.newBuilder()
                    .setAppliedCount(request.entriesCount)
                    .build(),
            )
            responseObserver.onCompleted()
        }
    }

    private companion object {
        val PROCESSED_AT: Instant = Instant.ofEpochMilli(1_760_000_000_000)
    }
}
