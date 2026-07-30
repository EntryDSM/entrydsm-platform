package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.config.LandingScheduleProperties
import hs.kr.entrydsm.application.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.application.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.application.adapterin.web.dto.request.CreateApplicantRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SubmitApplicationRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdateFamilyRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdateIntroductionRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdateMiddleSchoolRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdatePersonalRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdateStudyPlanRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.UpdateTypeRequest
import hs.kr.entrydsm.application.adapterin.web.dto.response.CreateApplicantResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.LandingResponse
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateIntroductionCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdatePersonalCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateStudyPlanCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateTypeCommand
import hs.kr.entrydsm.application.domain.enum.GuardianRelation
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import java.time.LocalDate
import java.time.YearMonth
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/application/v11/applicants")
class ApplicationController(
    private val applicationPort: ApplicationPort,
    private val landingScheduleProperties: LandingScheduleProperties,
) {
    @GetMapping("/landing")
    fun getLanding(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
    ): ApiResponse<LandingResponse> {
        val result = applicationPort.getLanding()
        return ApiResponse(data = result.toResponse(landingScheduleProperties))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createApplicant(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: CreateApplicantRequest,
    ): ApiResponse<CreateApplicantResponse> {
        val result = applicationPort.createApplicant(
            CreateApplicantCommand(accountId = request.accountId),
        )
        return ApiResponse(data = result.toResponse())
    }

    @PatchMapping("/{id}/type")
    fun updateType(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody request: UpdateTypeRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateType(
            UpdateTypeCommand(
                applicantId = id,
                admissionType = request.admissionType,
                region = request.region,
                graduationType = request.graduationType,
                graduationDate = request.graduationDate?.let(YearMonth::parse),
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/{id}/personal")
    fun updatePersonal(
        @PathVariable id: Long,
        @RequestBody request: UpdatePersonalRequest,
    ): ApiResponse<Unit> {
        applicationPort.updatePersonal(
            UpdatePersonalCommand(
                applicantId = id,
                photoFileId = request.photoFileId,
                name = request.name,
                phoneNumber = request.phoneNumber,
                gender = request.gender,
                birthdate = parseDate(request.birthdate),
                specialAdmissionType = request.specialAdmissionType ?: SpecialAdmissionType.NONE,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/{id}/family")
    fun updateFamily(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody request: UpdateFamilyRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateFamily(
            UpdateFamilyCommand(
                applicantId = id,
                guardianName = request.guardianName,
                guardianPhoneNumber = request.guardianPhoneNumber,
                guardianGender = request.guardianGender,
                guardianRelation = request.guardianRelation.toGuardianRelation(),
                zipCode = request.address.zipCode,
                addressBase = request.address.addressBase,
                addressDetail = request.address.addressDetail,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/{id}/middle-school")
    fun updateMiddleSchool(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody request: UpdateMiddleSchoolRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateMiddleSchool(
            UpdateMiddleSchoolCommand(
                applicantId = id,
                schoolName = request.schoolName,
                studentNumber = request.studentNumber,
                schoolPhone = request.schoolPhone,
                teacherName = request.teacherName,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/{id}/self-introduction")
    fun updateIntroduction(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody request: UpdateIntroductionRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateIntroduction(
            UpdateIntroductionCommand(id, request.introduction),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/{id}/study-plan")
    fun updateStudyPlan(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable id: Long,
        @RequestBody request: UpdateStudyPlanRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateStudyPlan(UpdateStudyPlanCommand(id, request.studyPlan))
        return ApiResponse(data = null)
    }

    @PatchMapping
    fun submit(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: SubmitApplicationRequest,
    ): ApiResponse<Unit> {
        applicationPort.submit(
            SubmitApplicationCommand(request.applicantId),
        )
        return ApiResponse(data = null)
    }

    private fun parseDate(value: String): LocalDate {
        return if (value.length == 7) {
            YearMonth.parse(value).atDay(1)
        } else {
            LocalDate.parse(value)
        }
    }

    private fun String.toGuardianRelation(): GuardianRelation {
        return when (uppercase()) {
            "FATHER", "FATHER_RELATION" -> GuardianRelation.FATHER
            "MOTHER", "MOTHER_RELATION" -> GuardianRelation.MOTHER
            "OTHER" -> GuardianRelation.OTHER
            else -> throw IllegalArgumentException("guardianRelation must be FATHER, MOTHER, or OTHER")
        }
    }
}
