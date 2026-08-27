package hs.kr.entrydsm.application.adapterin.web.dto.common

import hs.kr.entrydsm.application.adapterin.web.config.LandingScheduleProperties
import hs.kr.entrydsm.application.adapterin.web.dto.response.AcademicRecordResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.CreateApplicantResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.LandingResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.PeriodResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.ScheduleResponse
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult

fun CreateApplicantResult.toResponse(): CreateApplicantResponse =
    CreateApplicantResponse(applicantId = applicantId)

fun LandingResult.toResponse(scheduleProperties: LandingScheduleProperties): LandingResponse =
    LandingResponse(
        applicantName = applicantName,
        schedule = ScheduleResponse(
            applicationPeriod = PeriodResponse(
                startAt = scheduleProperties.applicationStartAt,
                endAt = scheduleProperties.applicationEndAt,
            ),
            resultAnnouncedAt = scheduleProperties.resultAnnouncedAt,
        ),
    )

fun AcademicRecordResult.toResponse(): AcademicRecordResponse =
    AcademicRecordResponse(
        absentCount = absentCount,
        earlyLeaveCount = earlyLeaveCount,
        lateCount = lateCount,
        classAbsenceCount = classAbsenceCount,
        volunteerTime = volunteerTime,
    )
