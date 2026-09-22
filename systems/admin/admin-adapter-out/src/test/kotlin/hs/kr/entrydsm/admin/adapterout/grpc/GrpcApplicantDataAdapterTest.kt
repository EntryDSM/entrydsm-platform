package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.ListApplicantsResponse
import hs.kr.entrydsm.application.grpc.UpdateApplicantArrivalRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import io.grpc.ServerBuilder
import io.grpc.Status
import io.grpc.stub.StreamObserver
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.Optional
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지원자 = application 의 원서 + admin 의 전형 정보입니다. 합치는 규칙과 메모리 필터·페이징을
 * 실제 직렬화를 거쳐 확인합니다.
 */
class GrpcApplicantDataAdapterTest {

    @Test
    fun `전형 정보가 없는 지원자는 미도착 심사 대기로 본다`() {
        val applicant = withAdapter(FakeApplicationService(listOf(applicant(id = 1L)))) {
            it.findAll().single()
        }

        assertEquals(1L, applicant.id)
        assertEquals("지원자1", applicant.name)
        assertEquals(Region.DAEJEON, applicant.region)
        assertEquals(AdmissionType.GENERAL, applicant.admissionType)
        assertEquals(GraduationStatus.EXPECTED, applicant.graduationStatus)
        assertFalse(applicant.isArrived)
        assertEquals(ApplicantStatus.PENDING, applicant.status)
        assertNull(applicant.examineeNumber)
    }

    @Test
    fun `전형 정보가 있으면 원서에 덧붙인다`() {
        val screening = ScreeningJpaEntity(
            applicantId = 1L,
            examineeNumber = "100001",
            isArrived = true,
            status = ApplicantStatus.FIRST_PASS,
            arrivedAt = Instant.EPOCH,
        )

        val applicant = withAdapter(FakeApplicationService(listOf(applicant(id = 1L))), listOf(screening)) {
            it.findAll().single()
        }

        assertEquals("100001", applicant.examineeNumber)
        assertTrue(applicant.isArrived)
        assertEquals(ApplicantStatus.FIRST_PASS, applicant.status)
        assertEquals(Instant.EPOCH, applicant.arrivedAt)
    }

    /** 이름은 application, 수험 번호는 admin 이 가져서 합친 뒤에만 함께 볼 수 있다. */
    @Test
    fun `키워드는 이름과 수험 번호를 함께 본다`() {
        val service = FakeApplicationService(
            listOf(applicant(id = 1L), applicant(id = 2L, name = "김철수")),
        )
        val screenings = listOf(ScreeningJpaEntity(applicantId = 1L, examineeNumber = "100777"))

        withAdapter(service, screenings) { adapter ->
            assertEquals(listOf(2L), adapter.findAll(ApplicantFilter(keyword = "철수")).map { it.id })
            assertEquals(listOf(1L), adapter.findAll(ApplicantFilter(keyword = "777")).map { it.id })
            assertEquals(emptyList<Long>(), adapter.findAll(ApplicantFilter(keyword = "없음")).map { it.id })
        }
    }

    @Test
    fun `원서 도착 여부와 전형 상태로 거른다`() {
        val service = FakeApplicationService(listOf(applicant(id = 1L), applicant(id = 2L)))
        val screenings = listOf(ScreeningJpaEntity(applicantId = 2L, isArrived = true))

        withAdapter(service, screenings) { adapter ->
            assertEquals(listOf(2L), adapter.findAll(ApplicantFilter(isArrived = true)).map { it.id })
            assertEquals(listOf(1L), adapter.findAll(ApplicantFilter(isArrived = false)).map { it.id })
            assertEquals(
                listOf(1L, 2L),
                adapter.findAll(ApplicantFilter(statuses = setOf(ApplicantStatus.PENDING))).map { it.id },
            )
        }
    }

    @Test
    fun `목록을 지원자 번호 순으로 정렬해 페이지로 자른다`() {
        val service = FakeApplicationService(
            listOf(applicant(id = 3L), applicant(id = 1L), applicant(id = 2L)),
        )

        val page = withAdapter(service) { it.search(ApplicantFilter(), PageRequest(page = 2, size = 2)) }

        assertEquals(listOf(3L), page.items.map { it.id })
        assertEquals(3L, page.totalElements)
        assertEquals(2, page.totalPages)
    }

    /** 원서 내용은 applicant id 가 아니라 원서 주인 계정(user_id)으로 찾는다. */
    @Test
    fun `상세는 원서 주인의 자기소개서 학업계획서 증명사진 ID 를 싣는다`() {
        val service = FakeApplicationService(
            listOf(applicant(id = 1L), applicant(id = 2L)),
            forms = listOf(
                ApplicationFormResponse.newBuilder()
                    .setApplicantId(1L)
                    .setUserId(101L)
                    .setPhotoFileId("photo_1")
                    .setIntroduction("첫 줄\n둘째 줄")
                    .setStudyPlan("계획")
                    .setSubjectScore(72.5)
                    .setAttendanceScore(15.0)
                    .setVolunteerScore(12.0)
                    .setAdditionalScore(3.0)
                    .setTotalScore(102.5)
                    .build(),
                ApplicationFormResponse.newBuilder().setApplicantId(2L).setUserId(102L).build(),
            ),
        )

        val (written, empty) = withAdapter(service) { it.findDetailById(1L)!! to it.findDetailById(2L)!! }

        assertEquals("지원자1", written.applicant.name)
        assertEquals("photo_1", written.photoFileId)
        assertEquals("첫 줄\n둘째 줄", written.introduction)
        assertEquals("계획", written.studyPlan)
        assertEquals(72.5, written.score?.subjectScore)
        assertEquals(15.0, written.score?.attendanceScore)
        assertEquals(12.0, written.score?.volunteerScore)
        assertEquals(3.0, written.score?.additionalScore)
        assertEquals(102.5, written.score?.totalScore)
        assertNull(empty.photoFileId)
        assertNull(empty.introduction)
        assertNull(empty.studyPlan)
        assertNull(empty.score)
    }

    @Test
    fun `없는 지원자는 null 이고 application 장애는 503 으로 옮긴다`() {
        withAdapter(FakeApplicationService(listOf(applicant(id = 1L)))) { adapter ->
            assertNull(adapter.findById(404L))
            assertNull(adapter.findDetailById(404L))
        }

        // UNIMPLEMENTED 는 ListApplicants 가 없는 옛 application 이 떠 있을 때다. 500 으로 나가면
        // 게이트웨이 서킷이 admin 라우트를 통째로 막으므로 다른 장애와 같은 503 으로 둔다.
        listOf(Status.UNAVAILABLE, Status.DEADLINE_EXCEEDED, Status.UNIMPLEMENTED).forEach { failure ->
            val exception = withAdapter(FakeApplicationService(emptyList(), failure = failure)) { adapter ->
                runCatching { adapter.findAll() }.exceptionOrNull()
            }

            assertTrue(failure.code.name, exception is AdminDomainException)
            assertEquals(
                failure.code.name,
                ErrorCode.APPLICATION_SERVICE_UNAVAILABLE,
                (exception as AdminDomainException).errorCode,
            )
        }
    }

    @Test
    fun `원서 도착 변경을 application 에 전달한다`() {
        val service = FakeApplicationService(listOf(applicant(id = 1L)))

        withAdapter(service) { it.update(1L, true) }

        assertEquals(1L, service.arrival?.applicantId)
        assertTrue(service.arrival?.isArrived == true)
    }

    @Test
    fun `제출되지 않은 원서의 도착 변경은 상태 전이 오류로 옮긴다`() {
        val exception = withAdapter(FakeApplicationService(emptyList(), failure = Status.FAILED_PRECONDITION)) {
            runCatching { it.update(1L, true) }.exceptionOrNull()
        }

        assertTrue(exception is AdminDomainException)
        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, (exception as AdminDomainException).errorCode)
    }

    private fun applicant(id: Long, name: String = "지원자$id"): ApplicantResponse =
        ApplicantResponse.newBuilder()
            .setApplicantId(id)
            .setUserId(id + 100)
            .setName(name)
            .setRegion(GrpcRegion.REGION_DAEJEON)
            .setAdmissionType(GrpcAdmissionType.ADMISSION_TYPE_REGULAR)
            .setGraduationType(GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE)
            .build()

    /** 실제 직렬화를 거치도록 로컬 포트에 가짜 application 서버를 띄운다. */
    private fun <T> withAdapter(
        application: FakeApplicationService,
        screenings: List<ScreeningJpaEntity> = emptyList(),
        block: (GrpcApplicantDataAdapter) -> T,
    ): T {
        val server = ServerBuilder.forPort(0).addService(application).build().start()
        val channel = ApplicationGrpcChannel("localhost", server.port, 3000, 3000)
        return try {
            block(GrpcApplicantDataAdapter(channel, screeningRepository(screenings)))
        } finally {
            channel.destroy()
            server.shutdownNow()
        }
    }

    /** DB 없이 읽기만 흉내 낸다. */
    private fun screeningRepository(screenings: List<ScreeningJpaEntity>): ScreeningJpaRepository =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ScreeningJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findAll" -> screenings
                "findById" -> Optional.ofNullable(screenings.find { it.applicantId == args[0] })
                else -> error("unexpected call: ${method.name}")
            }
        } as ScreeningJpaRepository

    private class FakeApplicationService(
        private val applicants: List<ApplicantResponse>,
        private val failure: Status? = null,
        private val forms: List<ApplicationFormResponse> = emptyList(),
    ) : ApplicationServiceGrpc.ApplicationServiceImplBase() {
        var arrival: UpdateApplicantArrivalRequest? = null

        override fun updateApplicantArrival(
            request: UpdateApplicantArrivalRequest,
            responseObserver: StreamObserver<ApplicationResponse>,
        ) {
            arrival = request
            respond(
                responseObserver,
                ApplicationResponse.newBuilder().setUserId(1L).build(),
            )
        }

        override fun getApplicationForm(
            request: GetApplicationFormRequest,
            responseObserver: StreamObserver<ApplicationFormResponse>,
        ) {
            val found = forms.find { it.userId == request.accountId }
                ?: return responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
            respond(responseObserver, found)
        }

        override fun listApplicants(
            request: ListApplicantsRequest,
            responseObserver: StreamObserver<ListApplicantsResponse>,
        ) = respond(
            responseObserver,
            ListApplicantsResponse.newBuilder().addAllApplicants(applicants).build(),
        )

        override fun getApplicant(
            request: GetApplicantRequest,
            responseObserver: StreamObserver<ApplicantResponse>,
        ) {
            val found = applicants.find { it.applicantId == request.applicantId }
                ?: return responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
            respond(responseObserver, found)
        }

        private fun <T> respond(observer: StreamObserver<T>, response: T) {
            if (failure != null) {
                observer.onError(failure.asRuntimeException())
                return
            }
            observer.onNext(response)
            observer.onCompleted()
        }
    }
}
