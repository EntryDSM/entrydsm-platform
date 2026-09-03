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
        assertEquals(applicationStartAt, response.data?.schedule?.applicationPeriod?.startAt)
        assertEquals(applicationEndAt, response.data?.schedule?.applicationPeriod?.endAt)
        assertEquals(resultAnnouncedAt, response.data?.schedule?.resultAnnouncedAt)
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
