package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity
import hs.kr.entrydsm.admin.adapterout.persistence.ApplicantProjectionStore
import hs.kr.entrydsm.common.crypto.SnapshotCipher
import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.ApplicationResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.AcademicRecord
import hs.kr.entrydsm.application.grpc.BatchGetApplicationFormsRequest
import hs.kr.entrydsm.application.grpc.BatchGetApplicationFormsResponse
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.DeleteApplicantRequest
import hs.kr.entrydsm.application.grpc.DeleteApplicantResponse
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GedScores
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.ListApplicantsResponse
import hs.kr.entrydsm.application.grpc.SemesterGrades
import hs.kr.entrydsm.application.grpc.UpdateApplicantArrivalRequest
import hs.kr.entrydsm.application.grpc.UpdateExamineeNumberRequest
import hs.kr.entrydsm.application.grpc.UpdateExamineeNumberResponse
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
    fun `출력 대사는 기존 프로젝션에 빠진 지원자도 복구한다`() {
        val forms = (1L..2L).map { ApplicationFormResponse.newBuilder().setApplicantId(it).setUserId(it + 100)
            .setName("지원자$it").setStatusVersion(1)
            .setApplicantStatus(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_SUBMITTED).build() }
        val service = FakeApplicationService(listOf(applicant(1), applicant(2)), forms = forms)
        val screenings = (1L..2L).map { ScreeningJpaEntity(applicantId = it, status = ApplicantStatus.FIRST_PASS) }
        withAdapter(service, screenings, projectedIds = setOf(1L)) { adapter ->
            assertEquals(listOf("0001"), adapter.findAdmissionFileRows().map { it.receiptNumber })
            adapter.syncExportProjection()
            assertEquals(listOf("0001", "0002"), adapter.findAdmissionFileRows().map { it.receiptNumber })
            assertEquals(listOf("0001", "0002"), adapter.findApplicationChecklistRows().map { it.receiptNumber })
            assertEquals(listOf(1L, 2L), adapter.findFirstPassApplicants().map { it.id })
        }
        assertEquals(1, service.batchCalls)
    }

    @Test
    fun `대사 응답에서 원서가 누락되면 부분 출력 대신 동기화 오류를 낸다`() {
        val form = ApplicationFormResponse.newBuilder().setApplicantId(1).setUserId(101).setStatusVersion(1)
            .setApplicantStatus(hs.kr.entrydsm.application.grpc.ApplicantStatus.APPLICANT_STATUS_SUBMITTED).build()
        val service = FakeApplicationService(listOf(applicant(1), applicant(2)), forms = listOf(form))
        withAdapter(service, projectedIds = emptySet()) { adapter ->
            val exception = runCatching { adapter.syncExportProjection() }.exceptionOrNull() as AdminDomainException
            assertEquals(ErrorCode.APPLICANT_SYNC_PENDING, exception.errorCode)
            assertTrue(adapter.findAdmissionFileRows().isEmpty())
        }
    }

    @Test
    fun `application 장애 중 다른 ID 100개의 상세는 로컬 데이터만 조회한다`() {
        val forms = (1L..100L).map { ApplicationFormResponse.newBuilder().setApplicantId(it).setUserId(it + 100)
            .setName("지원자$it").setIntroduction("소개$it").build() }
        val service = FakeApplicationService(emptyList(), Status.UNAVAILABLE, forms)
        withAdapter(service) { adapter ->
            (1L..100L).forEach { assertEquals("소개$it", adapter.findDetailById(it)?.introduction) }
        }
        assertEquals(0, service.detailCalls)
    }


    @Test
    fun `1차 합격자 존재 여부는 로컬 전형 정보로 확인한다`() {
        val screening = ScreeningJpaEntity(applicantId = 1L, status = ApplicantStatus.FIRST_PASS)

        val exists = withAdapter(FakeApplicationService(emptyList()), listOf(screening)) {
            it.hasFirstPassApplicants()
        }

        assertTrue(exists)
    }

    @Test
    fun `삭제 RPC에 지원자 번호를 전달하고 오류를 변환한다`() {
        val service = FakeApplicationService(emptyList())
        withAdapter(service) { it.delete(7L) }
        assertEquals(7L, service.deletedApplicantId)

        val exception = withAdapter(FakeApplicationService(emptyList(), Status.UNAVAILABLE)) {
            runCatching { it.delete(7L) }.exceptionOrNull()
        }
        assertEquals(ErrorCode.APPLICATION_SERVICE_UNAVAILABLE, (exception as AdminDomainException).errorCode)
    }

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
        assertEquals(Gender.MALE, applicant.gender)
        assertEquals("(34503) 대전광역시 유성구 가정북로 76", applicant.address)
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
                    .setGedScores(
                        GedScores.newBuilder()
                            .setKorean(95)
                            .setSociety(90)
                            .setHistory(85)
                            .setMath(80)
                            .setScience(75)
                            .setTechnology(70)
                            .setEnglish(65),
                    )
                    .build(),
                ApplicationFormResponse.newBuilder().setApplicantId(2L).setUserId(102L).build(),
            ),
        )

        val (written, empty) = withAdapter(service) { it.findDetailById(1L)!! to it.findDetailById(2L)!! }

        assertEquals(0, service.detailCalls)
        assertEquals("지원자1", written.applicant.name)
        assertEquals("photo_1", written.photoFileId)
        assertEquals("첫 줄\n둘째 줄", written.introduction)
        assertEquals("계획", written.studyPlan)
        assertEquals(72.5, written.score?.subjectScore)
        assertEquals(15.0, written.score?.attendanceScore)
        assertEquals(12.0, written.score?.volunteerScore)
        assertEquals(3.0, written.score?.additionalScore)
        assertEquals(102.5, written.score?.totalScore)
        assertEquals(listOf(95, 90, 85, 80, 75, 70, 65), written.gedScores?.let {
            listOf(it.korean, it.society, it.history, it.math, it.science, it.technology, it.english)
        })
        assertNull(empty.photoFileId)
        assertNull(empty.introduction)
        assertNull(empty.studyPlan)
        assertNull(empty.score)
        assertNull(empty.gedScores)
    }

    @Test
    fun `1차 합격자 원서만 한 번에 받아 명단 행으로 옮긴다`() {
        val service = FakeApplicationService(
            applicants = listOf(applicant(1L), applicant(2L), applicant(3L)),
            forms = listOf(
                ApplicationFormResponse.newBuilder()
                    .setApplicantId(1L)
                    .setUserId(101L)
                    .setName("홍길동")
                    .setAdmissionType(GrpcAdmissionType.ADMISSION_TYPE_REGULAR)
                    .setRegion(GrpcRegion.REGION_DAEJEON)
                    .setAdmissionTypeCode("3")
                    .setRegionCode("1")
                    .setSpecialAdmissionTypeCode("0")
                    .setClassNumber("2")
                    .setGedAverage(95.5)
                    .setThirdGradeFirstSemester(SemesterGrades.newBuilder().setKorean("A").setMath("B"))
                    .setAcademicRecord(AcademicRecord.newBuilder().setVolunteerTime(12))
                    .setSubjectScore(72.5)
                    .setTotalScore(99.5)
                    .build(),
                ApplicationFormResponse.newBuilder().setApplicantId(3L).setUserId(103L).build(),
            ),
        )
        val screenings = listOf(
            ScreeningJpaEntity(applicantId = 1L, status = ApplicantStatus.FIRST_PASS),
            ScreeningJpaEntity(applicantId = 2L, status = ApplicantStatus.FIRST_FAIL),
            ScreeningJpaEntity(applicantId = 3L, status = ApplicantStatus.FIRST_PASS),
        )

        val rows = withAdapter(service, screenings) { it.findFirstPassRows() }

        assertEquals(1, service.batchCalls)
        assertEquals(listOf(101L, 103L), service.batchAccountIds)
        assertEquals(listOf("0001", "0003"), rows.map { it.receiptNumber })
        assertEquals("310", rows[0].combinedCode)
        assertEquals("홍길동", rows[0].name)
        assertEquals("A", rows[0].thirdGradeFirstSemester.korean)
        assertEquals(9.0, rows[0].thirdGradeTotal)
        assertEquals(95.5, rows[0].gedAverage)
        assertNull(rows[1].combinedCode)
        assertNull(rows[1].admissionType)
    }

    @Test
    fun `없는 지원자는 null 이고 application 장애는 503 으로 옮긴다`() {
        withAdapter(FakeApplicationService(listOf(applicant(id = 1L)))) { adapter ->
            assertNull(adapter.findById(404L))
            assertEquals(ErrorCode.APPLICANT_SYNC_PENDING, (runCatching { adapter.findDetailById(404L) }.exceptionOrNull() as AdminDomainException).errorCode)
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
    fun `발급된 수험번호를 application 에 전달한다`() {
        val service = FakeApplicationService(emptyList())

        withAdapter(service) { it.saveAll(listOf(Applicant(id = 1L, examineeNumber = "11001"))) }

        assertEquals(1L, service.examineeNumberUpdate?.applicantId)
        assertEquals("11001", service.examineeNumberUpdate?.examineeNumber)
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
            .setGender(GrpcGender.GENDER_MALE)
            .setAddress("(34503) 대전광역시 유성구 가정북로 76")
            .build()

    /** 실제 직렬화를 거치도록 로컬 포트에 가짜 application 서버를 띄운다. */
    private fun <T> withAdapter(
        application: FakeApplicationService,
        screenings: List<ScreeningJpaEntity> = emptyList(),
        projectedIds: Set<Long>? = null,
        block: (GrpcApplicantDataAdapter) -> T,
    ): T {
        val server = ServerBuilder.forPort(0).addService(application).build().start()
        val channel = ApplicationGrpcChannel("localhost", server.port, 3000, 3000)
        val key = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        val cipher = SnapshotCipher("test", mapOf("test" to key))
        val projections = application.forms.filter { projectedIds == null || it.applicantId in projectedIds }.map { form ->
            val applicant = application.applicants.find { it.applicantId == form.applicantId }
            val local = form.toBuilder().also { builder ->
                applicant?.let { builder.setName(it.name).setRegion(it.region).setGender(it.gender)
                    .setGraduationType(it.graduationType).setAdmissionType(it.admissionType).setAddressBase(it.address) }
            }.build()
            ApplicantExportProjectionJpaEntity(form.applicantId, form.userId, cipher.encrypt(local.toByteArray()), form.statusVersion)
        }.toMutableList()
        val projectionRepository = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(ApplicantExportProjectionJpaRepository::class.java)) { _, method, args ->
                when (method.name) {
                    "findById" -> Optional.ofNullable(projections.find { it.applicantId == args[0] })
                    "findAllByDeletedFalse" -> projections.filter { !it.deleted }
                    "findAllByApplicantIdInAndDeletedFalse" -> projections.filter { !it.deleted && it.applicantId in (args[0] as Collection<Long>) }
                    "applyVersion" -> {
                        val current = projections.find { it.applicantId == args[0] }
                        if (current == null || current.eventVersion < args[3] as Long) {
                            projections.remove(current)
                            projections.add(ApplicantExportProjectionJpaEntity(args[0] as Long, args[1] as Long,
                                args[2] as ByteArray, args[3] as Long, args[4] as Boolean))
                        }
                        1
                    }
                    else -> error("unexpected projection call: ${method.name}")
                }
            } as ApplicantExportProjectionJpaRepository
        return try {
            block(
                GrpcApplicantDataAdapter(
                    channel,
                    screeningRepository(screenings),
                    projectionRepository,
                    ApplicantProjectionStore(projectionRepository, cipher),
                ),
            )
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
                "existsByStatus" -> screenings.any { it.status == args[0] }
                "findById" -> Optional.ofNullable(screenings.find { it.applicantId == args[0] })
                "saveAll" -> args[0]
                else -> error("unexpected call: ${method.name}")
            }
        } as ScreeningJpaRepository

    private fun <T> unusedRepository(type: Class<T>): T = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(type),
    ) { _, method, _ -> error("unexpected call: ${method.name}") } as T

    private class FakeApplicationService(
        val applicants: List<ApplicantResponse>,
        private val failure: Status? = null,
        val forms: List<ApplicationFormResponse> = emptyList(),
    ) : ApplicationServiceGrpc.ApplicationServiceImplBase() {
        var arrival: UpdateApplicantArrivalRequest? = null
        var examineeNumberUpdate: UpdateExamineeNumberRequest? = null
        var detailCalls = 0
        var batchCalls = 0
        var batchAccountIds: List<Long> = emptyList()
        var deletedApplicantId: Long? = null

        override fun deleteApplicant(
            request: DeleteApplicantRequest,
            responseObserver: StreamObserver<DeleteApplicantResponse>,
        ) {
            deletedApplicantId = request.applicantId
            respond(responseObserver, DeleteApplicantResponse.getDefaultInstance())
        }

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

        override fun updateExamineeNumber(
            request: UpdateExamineeNumberRequest,
            responseObserver: StreamObserver<UpdateExamineeNumberResponse>,
        ) {
            examineeNumberUpdate = request
            respond(responseObserver, UpdateExamineeNumberResponse.getDefaultInstance())
        }

        override fun getApplicationForm(
            request: GetApplicationFormRequest,
            responseObserver: StreamObserver<ApplicationFormResponse>,
        ) {
            detailCalls += 1
            val found = forms.find { it.userId == request.accountId }
                ?: return responseObserver.onError(Status.NOT_FOUND.asRuntimeException())
            respond(responseObserver, found)
        }

        override fun batchGetApplicationForms(
            request: BatchGetApplicationFormsRequest,
            responseObserver: StreamObserver<BatchGetApplicationFormsResponse>,
        ) {
            batchCalls += 1
            batchAccountIds = request.accountIdList
            respond(
                responseObserver,
                BatchGetApplicationFormsResponse.newBuilder()
                    .addAllApplications(forms.filter { it.userId in request.accountIdList })
                    .build(),
            )
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
            detailCalls += 1
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
