package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.config.LandingScheduleProperties
import hs.kr.entrydsm.application.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.application.adapterin.web.dto.common.toResponse
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
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import jakarta.validation.Valid
import java.time.LocalDate
import java.time.YearMonth
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/application/v11/applicants")
class ApplicationController(
    private val applicationPort: ApplicationPort,
    private val landingScheduleProperties: LandingScheduleProperties,
) {
    @GetMapping("/landing")
    fun getLanding(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
    ): ApiResponse<LandingResponse> {
        val result = applicationPort.getLanding(accountId)
        return ApiResponse(data = result.toResponse(landingScheduleProperties))
    }

    @PostMapping
    fun createApplicant(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
    ): ResponseEntity<ApiResponse<CreateApplicantResponse>> {
        val result = applicationPort.createApplicant(
            CreateApplicantCommand(
                accountId = accountId,
            ),
        )
        return ResponseEntity
            .status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(ApiResponse(data = result.toResponse()))
    }

    @PatchMapping("/type")
    fun updateType(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestHeader(SENSITIVE_AGREE_HEADER, defaultValue = "false") isSensitiveAgree: Boolean,
        @Valid @RequestBody request: UpdateTypeRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateType(
            UpdateTypeCommand(
                accountId = accountId,
                admissionType = request.admissionType,
                region = request.region,
                graduationType = request.graduationType,
                graduationDate = request.graduationDate?.let(YearMonth::parse),
                isSensitiveAgree = isSensitiveAgree,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/personal")
    fun updatePersonal(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @Valid @RequestBody request: UpdatePersonalRequest,
    ): ApiResponse<Unit> {
        applicationPort.updatePersonal(
            UpdatePersonalCommand(
                accountId = accountId,
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

    @PatchMapping("/family")
    fun updateFamily(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @Valid @RequestBody request: UpdateFamilyRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateFamily(
            UpdateFamilyCommand(
                accountId = accountId,
                guardianName = request.guardianName,
                guardianPhoneNumber = request.guardianPhoneNumber,
                guardianGender = request.guardianGender,
                guardianRelation = request.guardianRelation,
                zipCode = request.address.zipCode,
                addressBase = request.address.addressBase,
                addressDetail = request.address.addressDetail,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/middle-school")
    fun updateMiddleSchool(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @Valid @RequestBody request: UpdateMiddleSchoolRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateMiddleSchool(
            UpdateMiddleSchoolCommand(
                accountId = accountId,
                schoolCode = request.schoolCode,
                schoolName = request.schoolName,
                studentNumber = request.studentNumber,
                schoolPhone = request.schoolPhone,
                teacherName = request.teacherName,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/self-introduction")
    fun updateIntroduction(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @Valid @RequestBody request: UpdateIntroductionRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateIntroduction(
            UpdateIntroductionCommand(
                accountId = accountId,
                introduction = request.introduction,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping("/study-plan")
    fun updateStudyPlan(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @Valid @RequestBody request: UpdateStudyPlanRequest,
    ): ApiResponse<Unit> {
        applicationPort.updateStudyPlan(
            UpdateStudyPlanCommand(
                accountId = accountId,
                studyPlan = request.studyPlan,
            ),
        )
        return ApiResponse(data = null)
    }

    @PatchMapping
    fun submit(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
    ): ApiResponse<Unit> {
        applicationPort.submit(
            SubmitApplicationCommand(
                accountId = accountId,
            ),
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

    private companion object {
        const val USER_ID_HEADER = "X-USER-ID"
        const val SENSITIVE_AGREE_HEADER = "X-Sensitive-Agree"
    }
}
