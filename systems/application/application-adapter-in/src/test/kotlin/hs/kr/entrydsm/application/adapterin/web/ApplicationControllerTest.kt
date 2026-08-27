package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.config.LandingScheduleProperties
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationControllerTest {
    @Test
    fun createApplicantPassesAuthenticatedUser() {
        val applicationPort = FakeApplicationPort()
        val controller = ApplicationController(applicationPort, scheduleProperties())

        val response = controller.createApplicant(
            userId = 10L,
        )

        assertEquals(10L, applicationPort.createApplicantCommand?.userId)
        assertEquals(1L, response.data?.applicantId)
    }

    @Test
    fun getLandingReturnsConfiguredSchedule() {
        val controller = ApplicationController(FakeApplicationPort(), scheduleProperties())

        val response = controller.getLanding(10L)

        assertEquals("홍길동", response.data?.applicantName)
        assertEquals(APPLICATION_START_AT, response.data?.schedule?.applicationPeriod?.startAt)
        assertEquals(APPLICATION_END_AT, response.data?.schedule?.applicationPeriod?.endAt)
        assertEquals(RESULT_ANNOUNCED_AT, response.data?.schedule?.resultAnnouncedAt)
    }

    private class FakeApplicationPort : ApplicationPort {
        var createApplicantCommand: CreateApplicantCommand? = null

        override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
            createApplicantCommand = command
            return CreateApplicantResult(applicantId = 1L)
        }

        override fun updateType(command: UpdateTypeCommand) = Unit
        override fun updatePersonal(command: UpdatePersonalCommand) = Unit
        override fun updateFamily(command: UpdateFamilyCommand) = Unit
        override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) = Unit
        override fun updateIntroduction(command: UpdateIntroductionCommand) = Unit
        override fun updateStudyPlan(command: UpdateStudyPlanCommand) = Unit
        override fun submit(command: SubmitApplicationCommand) = Unit
        override fun getLanding(accountId: Long?): LandingResult = LandingResult(applicantName = "홍길동")
    }

    private companion object {
        val APPLICATION_START_AT: LocalDateTime = LocalDateTime.parse("2026-04-01T09:00:00")
        val APPLICATION_END_AT: LocalDateTime = LocalDateTime.parse("2026-04-30T17:00:00")
        val RESULT_ANNOUNCED_AT: LocalDateTime = LocalDateTime.parse("2026-05-15T10:00:00")

        fun scheduleProperties(): LandingScheduleProperties =
            LandingScheduleProperties(
                applicationStartAt = APPLICATION_START_AT,
                applicationEndAt = APPLICATION_END_AT,
                resultAnnouncedAt = RESULT_ANNOUNCED_AT,
            )
    }
}
