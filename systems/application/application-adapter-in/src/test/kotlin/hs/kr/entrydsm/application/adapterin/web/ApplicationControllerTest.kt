package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.config.LandingScheduleProperties
import hs.kr.entrydsm.application.adapterin.web.exception.GlobalExceptionHandler
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
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
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import hs.kr.entrydsm.application.application.exception.ApplicationAccessDeniedException

class ApplicationControllerTest {
    @Test
    fun saveConflictReturns409WithoutDatabaseDetails() {
        val port = object : ApplicationPort by FakeApplicationPort() {
            override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult =
                throw DataIntegrityViolationException("private database details")
        }
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationController(port, scheduleProperties()))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        val response = mvc.perform(post("/api/application/v11/applicants").header("user-id", "10"))
            .andReturn().response

        assertEquals(409, response.status)
        org.junit.Assert.assertTrue(response.contentAsString.contains("\"code\":\"DATA_INTEGRITY_VIOLATION\""))
        org.junit.Assert.assertFalse(response.contentAsString.contains("private database details"))
    }

    @Test
    fun createApplicantPassesAuthenticatedUser() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort, scheduleProperties())

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

        val response = ApplicationController(port, scheduleProperties()).createApplicant(accountId = 10L)

        assertEquals(200, response.statusCode.value())
        assertEquals(1L, response.body?.data?.applicantId)
    }

    @Test
    fun getLandingReturnsConfiguredSchedule() {
        val controller = ApplicationController(FakeApplicationPort(), scheduleProperties())

        val response = controller.getLanding(10L)

        assertEquals("홍길동", response.data?.applicantName)
        assertEquals(applicationStartAt, response.data?.schedule?.applicationPeriod?.startAt)
        assertEquals(applicationEndAt, response.data?.schedule?.applicationPeriod?.endAt)
        assertEquals(resultAnnouncedAt, response.data?.schedule?.resultAnnouncedAt)
    }

    @Test
    fun getLandingAllowsMissingResultAnnouncementSchedule() {
        val schedule = LandingScheduleProperties(applicationStartAt, applicationEndAt, null)
        val controller = ApplicationController(FakeApplicationPort(), schedule)

        val response = controller.getLanding(10L)

        assertNull(response.data?.schedule?.resultAnnouncedAt)
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
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(applicantName = "홍길동")
        override fun findByAccountId(accountId: Long): ApplicationSnapshotResult? = null
        override fun findApplicant(applicantId: Long): ApplicantResult? = null
        override fun findApplicationForm(accountId: Long): ApplicationFormResult? = null
        override fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult = error("not used")
    }

    private companion object {
        val applicationStartAt: LocalDateTime = LocalDateTime.parse("2026-10-19T09:00:00")
        val applicationEndAt: LocalDateTime = LocalDateTime.parse("2026-10-23T17:00:00")
        val resultAnnouncedAt: LocalDateTime = LocalDateTime.parse("2026-10-30T10:00:00")

        fun scheduleProperties(): LandingScheduleProperties =
            LandingScheduleProperties(
                applicationStartAt = applicationStartAt,
                applicationEndAt = applicationEndAt,
                resultAnnouncedAt = resultAnnouncedAt,
            )
    }
}
