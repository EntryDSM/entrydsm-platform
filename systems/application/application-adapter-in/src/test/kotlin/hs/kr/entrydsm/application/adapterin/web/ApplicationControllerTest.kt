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

class ApplicationControllerTest {
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
        val applicationStartAt: LocalDateTime = LocalDateTime.parse("2026-10-19T09:00:00")
        val applicationEndAt: LocalDateTime = LocalDateTime.parse("2026-10-23T17:00:00")
        val resultAnnouncedAt: LocalDateTime = LocalDateTime.parse("2026-10-30T10:00:00")

    }
}
