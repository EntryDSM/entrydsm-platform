package hs.kr.entrydsm.application.adapterin.web.dto.common

import hs.kr.entrydsm.application.adapterin.web.dto.response.AcademicRecordResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.CreateApplicantResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.LandingResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.MiddleSchoolResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.MiddleSchoolSearchResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.PeriodResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.ScheduleResponse
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.application.port.`in`.result.CreateApplicantResult
import hs.kr.entrydsm.application.application.port.`in`.result.LandingResult
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult

fun CreateApplicantResult.toResponse(): CreateApplicantResponse =
    CreateApplicantResponse(applicantId = applicantId)

fun LandingResult.toResponse(): LandingResponse =
    LandingResponse(
        applicantName = applicantName,
        schedule = ScheduleResponse(
            applicationPeriod = PeriodResponse(
                startAt = applicationStartAt,
                endAt = applicationEndAt,
            ),
            resultAnnouncedAt = resultAnnouncedAt,
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

fun MiddleSchoolSearchResult.toResponse(): MiddleSchoolSearchResponse =
    MiddleSchoolSearchResponse(
        schools = schools.map { MiddleSchoolResponse(code = it.code, name = it.name, address = it.address) },
        totalCount = totalCount,
    )
