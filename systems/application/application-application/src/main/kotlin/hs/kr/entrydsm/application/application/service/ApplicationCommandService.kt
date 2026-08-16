package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantAccessDeniedException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
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
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.GuardianRelation
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import java.time.LocalDate
import java.time.YearMonth
import org.springframework.stereotype.Service

@Service
class ApplicationCommandService(
    private val applicantRepository: ApplicantRepository,
) : ApplicationPort {
    override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
        return CreateApplicantResult(createApplicant(command.userId ?: command.accountId).id)
    }

    override fun updateType(command: UpdateTypeCommand) {
        updateType(
            applicantId = command.applicantId,
            userId = command.userId,
            admissionType = command.admissionType,
            region = command.region,
            graduationType = command.graduationType,
            graduationDate = command.graduationDate,
        )
    }

    override fun updatePersonal(command: UpdatePersonalCommand) {
        updatePersonal(
            applicantId = command.applicantId,
            userId = command.userId,
            photoFileId = command.photoFileId,
            name = command.name,
            phoneNumber = command.phoneNumber,
            gender = command.gender,
            birthdate = command.birthdate,
            specialAdmissionType = command.specialAdmissionType,
        )
    }

    override fun updateFamily(command: UpdateFamilyCommand) {
        updateFamily(
            applicantId = command.applicantId,
            userId = command.userId,
            guardianName = command.guardianName,
            guardianPhoneNumber = command.guardianPhoneNumber,
            guardianGender = command.guardianGender,
            guardianRelation = command.guardianRelation,
            zipCode = command.zipCode,
            addressBase = command.addressBase,
            addressDetail = command.addressDetail,
        )
    }

    override fun updateMiddleSchool(command: UpdateMiddleSchoolCommand) {
        updateMiddleSchool(
            applicantId = command.applicantId,
            userId = command.userId,
            schoolName = command.schoolName,
            studentNumber = command.studentNumber,
            schoolPhone = command.schoolPhone,
            teacherName = command.teacherName,
        )
    }

    override fun updateIntroduction(command: UpdateIntroductionCommand) {
        updateIntroduction(command.applicantId, command.userId, command.introduction)
    }

    override fun updateStudyPlan(command: UpdateStudyPlanCommand) {
        updateStudyPlan(command.applicantId, command.userId, command.studyPlan)
    }

    override fun submit(command: SubmitApplicationCommand) {
        submit(command.applicantId, command.userId)
    }

    override fun getLanding(accountId: Long?): LandingResult {
        return LandingResult(
            applicantName = accountId?.let(applicantRepository::findByAccountId)?.name,
        )
    }

    fun createApplicant(accountId: Long = 0): Applicant {
        return applicantRepository.save(
            Applicant(
                id = NEW_APPLICANT_ID,
                accountId = accountId,
            ),
        )
    }

    fun updateType(
        applicantId: Long,
        userId: Long? = null,
        admissionType: AdmissionType,
        region: Region,
        graduationType: GraduationType,
        graduationDate: YearMonth?,
    ) {
        val applicant = getApplicant(applicantId, userId)
        require(graduationType == GraduationType.GED || graduationDate != null) {
            "graduationDate is required unless graduationType is GED"
        }
        require(graduationType != GraduationType.GED || graduationDate == null) {
            "graduationDate must be null when graduationType is GED"
        }

        applicant.admissionType = admissionType
        applicant.region = region
        applicant.graduationType = graduationType
        applicant.graduationDate = graduationDate
        saveTouched(applicant)
    }

    fun updatePersonal(
        applicantId: Long,
        userId: Long? = null,
        photoFileId: Long,
        name: String,
        phoneNumber: String,
        gender: Gender,
        birthdate: LocalDate,
        specialAdmissionType: SpecialAdmissionType,
    ) {
        require(name.isNotBlank()) { "name is required" }
        require(phoneNumber.matches(PHONE_NUMBER_REGEX)) { "phoneNumber format is invalid" }

        val applicant = getApplicant(applicantId, userId)
        applicant.photoFileId = photoFileId
        applicant.name = name
        applicant.phoneNumber = phoneNumber
        applicant.gender = gender
        applicant.birthdate = birthdate
        applicant.specialAdmissionType = specialAdmissionType
        saveTouched(applicant)
    }

    fun updateFamily(
        applicantId: Long,
        userId: Long? = null,
        guardianName: String,
        guardianPhoneNumber: String,
        guardianGender: Gender,
        guardianRelation: GuardianRelation,
        zipCode: String,
        addressBase: String,
        addressDetail: String,
    ) {
        require(guardianName.isNotBlank()) { "guardianName is required" }
        require(guardianPhoneNumber.matches(PHONE_NUMBER_REGEX)) { "guardianPhoneNumber format is invalid" }
        require(zipCode.isNotBlank()) { "zipCode is required" }
        require(addressBase.isNotBlank()) { "addressBase is required" }
        require(addressDetail.isNotBlank()) { "addressDetail is required" }

        val applicant = getApplicant(applicantId, userId)
        applicant.guardianName = guardianName
        applicant.guardianPhoneNumber = guardianPhoneNumber
        applicant.guardianGender = guardianGender
        applicant.guardianRelation = guardianRelation
        applicant.zipCode = zipCode
        applicant.addressBase = addressBase
        applicant.addressDetail = addressDetail
        saveTouched(applicant)
    }

    fun updateMiddleSchool(
        applicantId: Long,
        userId: Long? = null,
        schoolName: String,
        studentNumber: String,
        schoolPhone: String,
        teacherName: String,
    ) {
        val applicant = getApplicant(applicantId, userId)
        require(applicant.graduationType != GraduationType.GED) {
            "middle school info is unavailable for GED applicants"
        }
        require(schoolName.isNotBlank()) { "schoolName is required" }
        require(studentNumber.isNotBlank()) { "studentNumber is required" }
        require(schoolPhone.isNotBlank()) { "schoolPhone is required" }
        require(teacherName.isNotBlank()) { "teacherName is required" }

        applicant.middleSchoolInfo = MiddleSchoolInfo(
            schoolName = schoolName,
            studentNumber = studentNumber,
            schoolPhone = schoolPhone,
            teacherName = teacherName,
        )
        saveTouched(applicant)
    }

    fun updateIntroduction(applicantId: Long, userId: Long? = null, introduction: String) {
        require(introduction.isNotBlank()) { "introduction is required" }
        require(introduction.length <= MAX_ESSAY_LENGTH) { "introduction is too long" }

        val applicant = getApplicant(applicantId, userId)
        applicant.introduction = introduction
        saveTouched(applicant)
    }

    fun updateStudyPlan(applicantId: Long, userId: Long? = null, studyPlan: String) {
        require(studyPlan.isNotBlank()) { "studyPlan is required" }
        require(studyPlan.length <= MAX_ESSAY_LENGTH) { "studyPlan is too long" }

        val applicant = getApplicant(applicantId, userId)
        applicant.studyPlan = studyPlan
        saveTouched(applicant)
    }

    fun submit(applicantId: Long, userId: Long? = null) {
        val applicant = getApplicant(applicantId, userId)
        require(applicant.admissionType != null) { "admission type is required" }
        require(!applicant.name.isNullOrBlank()) { "personal info is required" }
        require(!applicant.guardianName.isNullOrBlank()) { "family info is required" }
        require(!applicant.introduction.isNullOrBlank()) { "introduction is required" }
        require(!applicant.studyPlan.isNullOrBlank()) { "studyPlan is required" }
        saveTouched(applicant)
    }

    private fun getApplicant(applicantId: Long, userId: Long? = null): Applicant {
        val applicant = applicantRepository.findById(applicantId)
            ?: throw ApplicantNotFoundException(applicantId)
        if (userId != null && applicant.accountId != userId) {
            throw ApplicantAccessDeniedException(applicantId)
        }
        return applicant
    }

    private fun saveTouched(applicant: Applicant) {
        applicant.touch()
        applicantRepository.save(applicant)
    }

    companion object {
        private const val NEW_APPLICANT_ID = 0L
        private val PHONE_NUMBER_REGEX = Regex("^010-\\d{4}-\\d{4}$")
        private const val MAX_ESSAY_LENGTH = 1500
    }
}
