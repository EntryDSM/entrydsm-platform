package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.exception.GlobalExceptionHandler
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.exception.ApplicantAlreadyExistsException
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
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.Assert.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import hs.kr.entrydsm.application.application.exception.ApplicationAccessDeniedException
import hs.kr.entrydsm.application.application.service.ApplicationCommandService
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.domain.model.*
import hs.kr.entrydsm.application.domain.enum.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.junit.Assert.assertTrue

class ApplicationControllerTest {
    @Test
    fun `미입력 작성 원서는 필수 구조와 null 및 저장 기본값을 JSON에 보존한다`() {
        val fixture = DraftFixture(Applicant(id = 1, accountId = 10))
        val response = fixture.mvc.perform(get(DRAFT_URL).header("X-USER-ID", "10").header("X-User-Role", "STUDENT")).andReturn().response
        assertEquals(200, response.status)
        assertEquals(fixture.mapper.readTree("""{"success":true,"data":{"type":{"admissionType":null,"region":null,"graduationType":null,"graduationDate":null},"personal":{"photoFileId":null,"name":null,"phoneNumber":null,"gender":null,"birthdate":null,"specialAdmissionType":"NONE"},"family":{"guardianName":null,"guardianPhoneNumber":null,"guardianGender":null,"guardianRelation":null,"address":{"zipCode":null,"addressBase":null,"addressDetail":null}},"middleSchool":null,"evaluation":{"subjectGrades":[],"gedScores":null,"academicRecord":null,"certificates":null},"introduction":null,"studyPlan":null},"error":null}"""), fixture.mapper.readTree(response.contentAsString))
        assertEquals(listOf(10L), fixture.lookups)
    }

    @Test
    fun `저장 입력과 성적은 날짜 형식과 학기 순서를 지켜 반환하고 내부 정보는 제외한다`() {
        val grades = SubjectGrades(SubjectGrade.A, SubjectGrade.B, SubjectGrade.C, SubjectGrade.D, SubjectGrade.E, SubjectGrade.X, SubjectGrade.A)
        val applicant = Applicant(id = 99, accountId = 10, photoFileId = "photo_1", name = "지원자", phoneNumber = "01012345678",
            gender = Gender.FEMALE, birthdate = java.time.LocalDate.of(2010, 3, 4), admissionType = AdmissionType.REGULAR,
            region = Region.DAEJEON, graduationType = GraduationType.PROSPECTIVE, graduationDate = java.time.YearMonth.of(2027, 2),
            guardianName = "보호자", guardianPhoneNumber = "01087654321", guardianGender = Gender.MALE, guardianRelation = "부",
            zipCode = "01234", addressBase = "기본 주소", addressDetail = "상세 주소", introduction = "자기소개", studyPlan = "학업계획",
            middleSchoolInfo = MiddleSchoolInfo("code", "중학교", "30101", "-", "담임", "반환금지 학교주소"),
            academicRecord = AcademicRecord(absentCount = 1, lateCount = 2, earlyLeaveCount = 3, classAbsenceCount = 4, volunteerTime = 5,
                isDsmAlgorithmAwarded = true, subjectGrades = linkedMapOf(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER to grades,
                    SchoolSemester.SECOND_GRADE_FIRST_SEMESTER to grades), gedScores = GedScores(100, 90, 80, 70, 60)),
            totalScore = 123.45, examineeNumber = "11001", passStatus = PassResultStatus.PASS)
        val before = applicant.copy()
        val fixture = DraftFixture(applicant)
        val response = fixture.mvc.perform(get(DRAFT_URL).header("X-USER-ID", "10").header("X-User-Role", "STUDENT")
            .param("accountId", "20").param("applicantId", "99")).andReturn().response
        assertEquals(200, response.status)
        val expected = """{"success":true,"data":{"type":{"admissionType":"REGULAR","region":"DAEJEON","graduationType":"PROSPECTIVE","graduationDate":"2027-02"},"personal":{"photoFileId":"photo_1","name":"지원자","phoneNumber":"01012345678","gender":"FEMALE","birthdate":"2010-03-04","specialAdmissionType":"NONE"},"family":{"guardianName":"보호자","guardianPhoneNumber":"01087654321","guardianGender":"MALE","guardianRelation":"부","address":{"zipCode":"01234","addressBase":"기본 주소","addressDetail":"상세 주소"}},"middleSchool":{"schoolCode":"code","schoolName":"중학교","studentNumber":"30101","schoolPhone":"-","teacherName":"담임"},"evaluation":{"subjectGrades":[{"schoolSemester":"2-1","subjects":{"koreanGrade":"A","mathGrade":"B","englishGrade":"C","scienceGrade":"D","societyGrade":"E","technologyGrade":"X","historyGrade":"A"}},{"schoolSemester":"3-1","subjects":{"koreanGrade":"A","mathGrade":"B","englishGrade":"C","scienceGrade":"D","societyGrade":"E","technologyGrade":"X","historyGrade":"A"}}],"gedScores":{"koreanScore":100,"mathScore":90,"englishScore":80,"scienceScore":70,"societyScore":60,"technologyScore":null},"academicRecord":{"absentCount":1,"lateCount":2,"earlyLeaveCount":3,"classAbsenceCount":4,"volunteerTime":5},"certificates":{"isDsmAlgorithmAwarded":true,"isProgrammingCertified":false}},"introduction":"자기소개","studyPlan":"학업계획"},"error":null}"""
        assertEquals(fixture.mapper.readTree(expected), fixture.mapper.readTree(response.contentAsString))
        assertEquals(before, applicant)
        assertEquals(listOf(10L), fixture.lookups)
    }

    @Test
    fun `원서 없음과 모든 비작성 상태는 정해진 HTTP 오류로 응답한다`() {
        for (status in listOf(null) + ApplicantStatus.entries.filter { it != ApplicantStatus.DRAFT }) {
            val fixture = DraftFixture(status?.let { Applicant(id = 1, accountId = 10, status = it) })
            val response = fixture.mvc.perform(get(DRAFT_URL).header("X-USER-ID", "10").header("X-User-Role", "STUDENT")).andReturn().response
            assertEquals("$status: ${response.contentAsString}", if (status == null) 404 else 400, response.status)
            val root = fixture.mapper.readTree(response.contentAsString)
            assertEquals(false, root.path("success").asBoolean())
            org.junit.Assert.assertFalse(root.has("data"))
            assertEquals(if (status == null) "APPLICANT_NOT_FOUND" else "APPLICATION_NOT_READABLE", root.path("error").path("code").asString())
            if (status != null) assertEquals("작성 중인 원서만 조회할 수 있습니다.", root.path("error").path("message").asString())
        }
    }

    @Test
    fun `조회는 미인증과 학생 외 권한을 차단하고 요청 계정으로 타인 원서를 조회하지 않는다`() {
        val fixture = DraftFixture(Applicant(id = 1, accountId = 20))
        assertEquals(401, fixture.mvc.perform(get(DRAFT_URL)).andReturn().response.status)
        for (role in listOf("ADMIN", "TEACHER", "")) {
            assertEquals(403, fixture.mvc.perform(get(DRAFT_URL).header("X-USER-ID", "10").header("X-User-Role", role)).andReturn().response.status)
        }
        assertTrue(fixture.lookups.isEmpty())
        assertEquals(404, fixture.mvc.perform(get(DRAFT_URL).header("X-USER-ID", "10").header("X-User-Role", "STUDENT")
            .param("accountId", "20").param("applicantId", "1")).andReturn().response.status)
        assertEquals(listOf(10L), fixture.lookups)
    }

    private class DraftFixture(applicant: Applicant?) {
        val lookups = mutableListOf<Long>()
        val mapper = tools.jackson.module.kotlin.jacksonMapperBuilder().build()
        private val repository = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicantRepository::class.java)) { _, method, args ->
            check(method.name == "findByAccountId") { "조회 중 예상하지 않은 저장소 호출: ${method.name}" }
            val accountId = args[0] as Long
            lookups += accountId
            applicant?.takeIf { it.accountId == accountId }
        } as ApplicantRepository
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(ApplicationCommandService(repository,
            ApplicationPeriodReader { error("조회 중 기간 확인 금지") }, hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator { _, _ -> error("전화번호 검증 금지") })))
            .setMessageConverters(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(mapper))
            .addInterceptors(ApplicationAuthorizationInterceptor())
            .setControllerAdvice(GlobalExceptionHandler()).build()
    }

    @Test
    fun middleSchoolPassesLiteralDashForBothContactFields() {
        var command: UpdateMiddleSchoolCommand? = null
        val port = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(ApplicationPort::class.java)) { _, method, args ->
            assertEquals("updateMiddleSchool", method.name)
            command = args[0] as UpdateMiddleSchoolCommand
            Unit
        } as ApplicationPort
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(port))
            .setMessageConverters(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(tools.jackson.module.kotlin.jacksonMapperBuilder().build()))
            .setControllerAdvice(GlobalExceptionHandler()).build()
        val response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/application/v11/applicants/middle-school")
            .header("X-USER-ID", "10").contentType("application/json")
            .content("""{"schoolCode":"code","schoolName":"학교","studentNumber":"30101","schoolPhone":"-","teacherName":"-"}""")).andReturn().response
        assertEquals(200, response.status)
        assertEquals("-", command!!.schoolPhone)
        assertEquals("-", command!!.teacherName)
    }

    @Test
    fun fieldErrorsReturnSpecificCodesWithoutInputDetails() {
        val mapper = tools.jackson.module.kotlin.jacksonMapperBuilder().build()
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(FakeApplicationPort()))
            .setMessageConverters(org.springframework.http.converter.json.JacksonJsonHttpMessageConverter(mapper))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
        val personal = """{"photoFileId":"photo_123","name":"홍길동","phoneNumber":"010-1234-5678","gender":"MALE","birthdate":"2010-01-01"}"""
        val cases = listOf(
            Triple("personal", personal.replace("010-1234-5678", "private-phone"), "PHONE_NUMBER_INVALID_FORMAT"),
            Triple("personal", personal.replace("홍길동", ""), "NAME_REQUIRED"),
            Triple("personal", personal.replace("홍길동", "가".repeat(21)), "NAME_TOO_LONG"),
            Triple("personal", personal.replace("\"name\":\"홍길동\",", ""), "NAME_REQUIRED"),
            Triple("personal", personal.replace("\"name\":\"홍길동\"", "\"name\":null"), "NAME_REQUIRED"),
            Triple("personal", personal.replace("MALE", "unknown"), "GENDER_INVALID_VALUE"),
            Triple("personal", personal.replace("2010-01-01", "2010-02-30"), "BIRTHDATE_INVALID_FORMAT"),
            Triple("type", """{"admissionType":"REGULAR","region":"DAEJEON","graduationType":"PROSPECTIVE","graduationDate":"2027-13"}""", "GRADUATION_DATE_INVALID_FORMAT"),
            *listOf("2025-13", "2025-00", "2025", "2025-2", "2025-02-01", "abcd-02").map { date ->
                Triple("type", """{"admissionType":"REGULAR","region":"DAEJEON","graduationType":"GRADUATED","graduationDate":"$date"}""", "GRADUATION_DATE_INVALID_FORMAT")
            }.toTypedArray(),
            Triple("family", """{"guardianName":"보호자","guardianPhoneNumber":"010-1234-5678","guardianGender":"MALE","guardianRelation":"부","address":{"zipCode":"","addressBase":"주소","addressDetail":"상세"}}""", "ADDRESS_ZIP_CODE_REQUIRED"),
            Triple("personal", personal.replace("\"홍길동\"", "{}"), "NAME_INVALID_TYPE"),
        )
        for ((path, body, code) in cases) {
            val response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/application/v11/applicants/$path")
                .header("X-USER-ID", "10").contentType("application/json").content(body)).andReturn().response
            assertEquals("$path / $code: ${response.contentAsString}", 400, response.status)
            val error = mapper.readTree(response.contentAsString).get("error")
            assertEquals("APPLICATION_$code", error.get("code").asString())
            assertEquals(hs.kr.entrydsm.application.application.exception.ApplicationErrorCode.valueOf("APPLICATION_$code").message, error.get("message").asString())
            org.junit.Assert.assertFalse(response.contentAsString.contains("private-phone"))
        }
        for (date in listOf("2010-01", "2010-01-01")) {
            assertEquals(200, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/application/v11/applicants/personal")
                .header("X-USER-ID", "10").contentType("application/json").content(personal.replace("2010-01-01", date))).andReturn().response.status)
        }
        val multiple = personal.replace("2010-01-01", "").replace("홍길동", "").replace("010-1234-5678", "bad")
        val response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/application/v11/applicants/personal")
            .header("X-USER-ID", "10").contentType("application/json").content(multiple)).andReturn().response
        assertEquals("APPLICATION_BIRTHDATE_REQUIRED", mapper.readTree(response.contentAsString).get("error").get("code").asString())
        val malformed = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/application/v11/applicants/personal")
            .header("X-USER-ID", "10").contentType("application/json").content("{private-input")).andReturn().response
        assertEquals(400, malformed.status)
        assertEquals("INVALID_REQUEST", mapper.readTree(malformed.contentAsString).get("error").get("code").asString())
        org.junit.Assert.assertFalse(malformed.contentAsString.contains("private-input"))
    }

    @Test
    fun submittedApplicantCreateReturns409() {
        val port = object : ApplicationPort by FakeApplicationPort() {
            override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult =
                throw ApplicantAlreadyExistsException(requireNotNull(command.accountId))
        }
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(port))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        val response = mvc.perform(post("/api/application/v11/applicants").header("X-USER-ID", "10"))
            .andReturn().response

        assertEquals(409, response.status)
        org.junit.Assert.assertTrue(response.contentAsString.contains("\"code\":\"APPLICANT_ALREADY_EXISTS\""))
    }

    @Test
    fun saveConflictReturns409WithoutDatabaseDetails() {
        val port = object : ApplicationPort by FakeApplicationPort() {
            override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult =
                throw DataIntegrityViolationException("private database details")
        }
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(port))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        val response = mvc.perform(post("/api/application/v11/applicants").header("X-USER-ID", "10"))
            .andReturn().response

        assertEquals(409, response.status)
        org.junit.Assert.assertTrue(response.contentAsString.contains("\"code\":\"DATA_INTEGRITY_VIOLATION\""))
        org.junit.Assert.assertFalse(response.contentAsString.contains("private database details"))
    }

    @Test
    fun createApplicantPassesAuthenticatedUser() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort)

        val response = controller.createApplicant(
            accountId = 10L,
        )

        assertEquals(10L, applicationPort.createApplicantCommand?.accountId)
        assertEquals(201, response.statusCode.value())
        assertEquals(1L, response.body?.data?.applicantId)
    }

    @Test
    fun createApplicantReturnsExistingApplicantWith200() {
        val port = object : ApplicationPort by FakeApplicationPort() {
            override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult =
                FakeApplicationPort().createApplicant(command).copy(created = false)
        }

        val response = ApplicationController(port).createApplicant(accountId = 10L)

        assertEquals(200, response.statusCode.value())
        assertEquals(1L, response.body?.data?.applicantId)
    }

    @Test
    fun getLandingReturnsDatabaseSchedule() {
        val controller = ApplicationController(FakeApplicationPort())

        val response = controller.getLanding(10L)

        assertEquals("홍길동", response.data?.applicantName)
        assertEquals(applicationStartAt, response.data?.schedule?.applicationPeriod?.startAt)
        assertEquals(applicationEndAt, response.data?.schedule?.applicationPeriod?.endAt)
        assertEquals(resultAnnouncedAt, response.data?.schedule?.resultAnnouncedAt)
    }

    @Test
    fun getLandingAllowsMissingResultAnnouncementSchedule() {
        val port = object : ApplicationPort by FakeApplicationPort() {
            override fun getLanding(accountId: Long?) = LandingResult("홍길동", applicationStartAt, applicationEndAt)
        }
        val controller = ApplicationController(port)

        val response = controller.getLanding(10L)

        assertNull(response.data?.schedule?.resultAnnouncedAt)
    }

    @Test
    fun landingPreservesJsonStructureAndDateFormatWithoutScheduleProperties() {
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(FakeApplicationPort())).build()
        val httpResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/application/v11/applicants/landing")
            .header("X-USER-ID", "10")).andReturn().response
        assertEquals(200, httpResponse.status)
        val schedule = tools.jackson.module.kotlin.jacksonMapperBuilder().build()
            .readTree(httpResponse.contentAsString).get("data").get("schedule")
        assertEquals("2026-10-19T09:00:00", schedule.get("applicationPeriod").get("startAt").asString())
        assertEquals("2026-10-23T17:00:00", schedule.get("applicationPeriod").get("endAt").asString())
        assertEquals("2026-10-30T10:00:00", schedule.get("resultAnnouncedAt").asString())
        val missing = object : ApplicationPort by FakeApplicationPort() {
            override fun getLanding(accountId: Long?) = LandingResult(null)
        }
        val response = ApplicationController(missing).getLanding(10L)
        assertNotNull(response.data?.schedule?.applicationPeriod)
        assertNull(response.data?.schedule?.applicationPeriod?.startAt)
        assertNull(response.data?.schedule?.applicationPeriod?.endAt)
    }

    @Test
    fun applicationApiAllowsOnlyStudentRole() {
        val interceptor = ApplicationAuthorizationInterceptor()
        val request = MockHttpServletRequest().apply {
            addHeader("X-User-Id", "10")
            addHeader("X-User-Role", "STUDENT")
        }

        assertEquals(true, interceptor.preHandle(request, MockHttpServletResponse(), Any()))
        request.removeHeader("X-User-Role")
        request.addHeader("X-User-Role", "ADMIN")
        assertThrows(ApplicationAccessDeniedException::class.java) {
            interceptor.preHandle(request, MockHttpServletResponse(), Any())
        }
    }

    private class FakeApplicationPort : ApplicationPort {
        override fun findDraftApplication(accountId: Long): hs.kr.entrydsm.application.application.port.`in`.result.DraftApplicationResult = error("not used")
        var createApplicantCommand: CreateApplicantCommand? = null

        override fun listApplicants(): List<ApplicantResult> = emptyList()

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            createApplicantCommand = command
            return CreateApplicantResult(
                applicantId = 1L,
                snapshot = ApplicationSnapshotResult(
                    accountId = requireNotNull(command.accountId),
                    applicantStatus = ApplicantStatus.DRAFT,
                    submittedAt = null,
                    updatedAt = applicationStartAt,
                    passStatus = PassResultStatus.PENDING,
                    announcedAt = null,
                ),
            )
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult("홍길동", applicationStartAt, applicationEndAt, resultAnnouncedAt)
        override fun findByAccountId(accountId: Long): ApplicationSnapshotResult? = null
        override fun findApplicant(applicantId: Long): ApplicantResult? = null
        override fun findApplicationForm(accountId: Long): ApplicationFormResult? = null
        override fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult = error("not used")
        override fun deleteApplicant(applicantId: Long) = Unit
    }

    private companion object {
        const val DRAFT_URL = "/api/application/v11/applicants"
        val applicationStartAt: LocalDateTime = LocalDateTime.parse("2026-10-19T09:00:00")
        val applicationEndAt: LocalDateTime = LocalDateTime.parse("2026-10-23T17:00:00")
        val resultAnnouncedAt: LocalDateTime = LocalDateTime.parse("2026-10-30T10:00:00")

    }
}
