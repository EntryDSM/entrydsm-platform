package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.ApplicationCancelNotAllowedException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import hs.kr.entrydsm.application.application.exception.SensitiveConsentRequiredException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CreateApplicantCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SubmitApplicationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateFamilyCommand
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateApplicantArrivalCommand
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
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.Gender
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.Region
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SpecialAdmissionType
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.MiddleSchoolInfo
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.springframework.transaction.annotation.Transactional

@Transactional
class ApplicationCommandService(
    private val applicantRepository: ApplicantRepository,
    private val applicationPeriod: ApplicationPeriodReader,
    private val applicantStatusEventOutbox: ApplicantStatusEventOutbox = ApplicantStatusEventOutbox {},
) : ApplicationPort {
    private val scoreCalculator = ScoreCalculator()

    override fun createApplicant(command: CreateApplicantCommand): CreateApplicantResult {
        val accountId = requireAccountId(command.accountId)
        val existing = applicantRepository.findByAccountId(accountId)
        val applicant = existing ?: createApplicant(accountId)
        return CreateApplicantResult(applicant.id, applicant.toSnapshot(), created = existing == null)
    }

    override fun updateType(command: UpdateTypeCommand) {
        if (command.admissionType == AdmissionType.SOCIAL && !command.isSensitiveAgree) {
            throw SensitiveConsentRequiredException()
        }
        updateType(
            accountId = command.accountId,
            admissionType = command.admissionType,
            region = command.region,
            graduationType = command.graduationType,
            graduationDate = command.graduationDate,
        )
    }

    override fun updatePersonal(command: UpdatePersonalCommand) {
        updatePersonal(
            accountId = command.accountId,
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
            accountId = command.accountId,
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
            accountId = command.accountId,
            schoolCode = command.schoolCode,
            schoolName = command.schoolName,
            studentNumber = command.studentNumber,
            schoolPhone = command.schoolPhone,
            teacherName = command.teacherName,
        )
    }

    override fun updateIntroduction(command: UpdateIntroductionCommand) {
        updateIntroduction(command.accountId, command.introduction)
    }

    override fun updateStudyPlan(command: UpdateStudyPlanCommand) {
        updateStudyPlan(command.accountId, command.studyPlan)
    }

    override fun submit(command: SubmitApplicationCommand) {
        submit(command.accountId)
    }

    override fun updateArrival(command: UpdateApplicantArrivalCommand): ApplicationSnapshotResult {
        val applicant = applicantRepository.findById(command.applicantId)
            ?: throw ApplicantNotFoundException(command.applicantId)
        val target = if (command.isArrived) ApplicantStatus.ARRIVAL else ApplicantStatus.SUBMITTED

        if (applicant.status == target) return applicant.toSnapshot()
        require(applicant.status in setOf(ApplicantStatus.SUBMITTED, ApplicantStatus.ARRIVAL)) {
            "arrival can only be changed for a submitted application"
        }

        applicant.status = target
        applicant.statusVersion += 1
        return saveTouched(applicant).also(::publishStatus).toSnapshot()
    }

    override fun getLanding(accountId: Long?): LandingResult {
        return LandingResult(
            applicantName = accountId?.let(applicantRepository::findByAccountId)?.name,
        )
    }

    override fun findByAccountId(accountId: Long): ApplicationSnapshotResult? =
        applicantRepository.findByAccountId(accountId)?.toSnapshot()

    override fun findApplicant(applicantId: Long): ApplicantResult? =
        applicantRepository.findById(applicantId)?.toApplicantResult()

    override fun listApplicants(): List<ApplicantResult> =
        applicantRepository.findSummariesByStatusIn(APPLIED_STATUSES)

    override fun findApplicationForm(accountId: Long): ApplicationFormResult? =
        applicantRepository.findByAccountId(accountId)?.toApplicationFormResult()

    override fun findApplicationForms(accountIds: List<Long>): List<ApplicationFormResult> =
        applicantRepository.findAllByAccountIdIn(accountIds.distinct()).map { it.toApplicationFormResult() }

    private fun Applicant.toApplicantResult(): ApplicantResult = ApplicantResult(
        applicantId = id,
        accountId = accountId,
        name = name,
        schoolName = middleSchoolInfo?.schoolName,
        region = region,
        admissionType = admissionType,
        photoFileId = photoFileId,
        birthdate = birthdate,
        phoneNumber = phoneNumber,
        graduationType = graduationType,
        totalScore = totalScore,
        status = status,
        submittedAt = submittedAt,
        gender = gender,
        address = addressBase,
    )

    private fun Applicant.toApplicationFormResult(): ApplicationFormResult {
        val grades = academicRecord?.subjectGrades.orEmpty()
        /*
         * 서식 1 의 "직전학기"·"직전전학기" 는 절대 학기가 아니라 자유학기를 건너뛴 상대 순서다.
         * ScoreCalculator 가 반영 학기를 고르는 순서(2-2 → 2-1 → 1-2 → 1-1)와 같아야
         * 인쇄한 원서와 산출한 점수가 어긋나지 않는다.
         */
        val previous = PREVIOUS_SEMESTERS.mapNotNull(grades::reflected)
        return ApplicationFormResult(
            applicantId = id,
            accountId = accountId,
            status = status,
            name = name,
            phoneNumber = phoneNumber,
            birthdate = birthdate,
            gender = gender,
            address = formatAddress(),
            photoFileId = photoFileId,
            region = region,
            admissionType = admissionType,
            specialAdmissionType = specialAdmissionType,
            graduationType = graduationType,
            graduationDate = graduationDate,
            guardianName = guardianName,
            guardianRelation = guardianRelation,
            guardianPhoneNumber = guardianPhoneNumber,
            middleSchool = middleSchoolInfo,
            thirdGradeSecondSemester = grades.reflected(SchoolSemester.THIRD_GRADE_SECOND_SEMESTER),
            thirdGradeFirstSemester = grades.reflected(SchoolSemester.THIRD_GRADE_FIRST_SEMESTER),
            previousSemester = previous.getOrNull(0),
            secondPreviousSemester = previous.getOrNull(1),
            academicRecord = academicRecord,
            score = totalScore?.let { scoreCalculator.calculateBreakdown(this).copy(totalScore = it) },
            introduction = introduction,
            studyPlan = studyPlan,
            classNumber = middleSchoolInfo?.studentNumber
                ?.let(STUDENT_NUMBER::matchEntire)
                ?.groupValues
                ?.get(1)
                ?.trimStart('0')
                ?.ifEmpty { "0" },
            studentNumber = middleSchoolInfo?.studentNumber,
            gedAverage = academicRecord?.gedScores?.let {
                listOf(
                    it.koreanScore,
                    it.societyScore,
                    it.historyScore,
                    it.mathScore,
                    it.scienceScore,
                    it.technologyScore,
                    it.englishScore,
                ).average()
            },
        )
    }

    /** 원서에 적힌 주소를 서식 1 의 한 칸에 넣을 한 줄로 만든다. 아무것도 없으면 null. */
    private fun Applicant.formatAddress(): String? = listOfNotNull(
        zipCode?.takeIf(String::isNotBlank)?.let { "($it)" },
        addressBase?.takeIf(String::isNotBlank),
        addressDetail?.takeIf(String::isNotBlank),
    ).joinToString(" ").takeIf(String::isNotBlank)

    override fun cancel(accountId: Long, reason: String?): ApplicationSnapshotResult {
        val applicant = getApplicantByAccountId(accountId)
        if (applicant.status != ApplicantStatus.SUBMITTED) {
            throw ApplicationCancelNotAllowedException()
        }
        applicant.status = ApplicantStatus.CANCELED
        applicant.statusVersion += 1
        applicant.cancelReason = reason?.takeIf(String::isNotBlank)
        return saveTouched(applicant).also(::publishStatus).toSnapshot()
    }

    fun createApplicant(accountId: Long = 0): Applicant {
        // 이미 있는 원서는 기간이 끝나도 돌려준다. applicantId 를 받는 유일한 경로라 수험표 출력에 쓴다.
        applicantRepository.findByAccountId(accountId)?.let { return it }
        applicationPeriod.requireOpen()
        val applicant = applicantRepository.save(
            Applicant(
                id = NEW_APPLICANT_ID,
                accountId = accountId,
                statusVersion = 1,
            ),
        )
        publishStatus(applicant)
        return applicant
    }

    fun updateType(
        accountId: Long?,
        admissionType: AdmissionType,
        region: Region,
        graduationType: GraduationType,
        graduationDate: YearMonth?,
    ) {
        val applicant = getWritableApplicant(accountId)
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
        if (graduationType == GraduationType.GED) {
            applicant.middleSchoolInfo = null
            applicant.academicRecord?.subjectGrades?.clear()
        }
        saveTouched(applicant)
    }

    private fun publishStatus(applicant: Applicant) = applicantStatusEventOutbox.add(
        ApplicantStatusChanged(
            accountId = applicant.accountId,
            applicantId = applicant.id,
            status = applicant.status,
            occurredAt = applicant.updatedAt,
            version = applicant.statusVersion,
            submittedAt = applicant.submittedAt,
            passStatus = applicant.passStatus,
            announcedAt = applicant.announcedAt,
        ),
    )

    fun updatePersonal(
        accountId: Long?,
        photoFileId: String,
        name: String,
        phoneNumber: String,
        gender: Gender,
        birthdate: LocalDate,
        specialAdmissionType: SpecialAdmissionType,
    ) {
        require(photoFileId.isNotBlank() && photoFileId.length <= MAX_PHOTO_FILE_ID_LENGTH) { "photoFileId is invalid" }
        require(name.isNotBlank()) { "name is required" }
        require(phoneNumber.matches(PHONE_NUMBER_REGEX)) { "phoneNumber format is invalid" }

        val applicant = getWritableApplicant(accountId)
        applicant.photoFileId = photoFileId
        applicant.name = name
        applicant.phoneNumber = phoneNumber
        applicant.gender = gender
        applicant.birthdate = birthdate
        applicant.specialAdmissionType = specialAdmissionType
        saveTouched(applicant)
    }

    fun updateFamily(
        accountId: Long?,
        guardianName: String,
        guardianPhoneNumber: String,
        guardianGender: Gender,
        guardianRelation: String,
        zipCode: String,
        addressBase: String,
        addressDetail: String,
    ) {
        require(guardianName.isNotBlank()) { "guardianName is required" }
        require(guardianPhoneNumber.matches(PHONE_NUMBER_REGEX)) { "guardianPhoneNumber format is invalid" }
        require(zipCode.isNotBlank()) { "zipCode is required" }
        require(addressBase.isNotBlank()) { "addressBase is required" }
        require(addressDetail.isNotBlank()) { "addressDetail is required" }

        val applicant = getWritableApplicant(accountId)
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
        accountId: Long?,
        schoolCode: String,
        schoolName: String,
        studentNumber: String,
        schoolPhone: String,
        teacherName: String,
    ) {
        val applicant = getWritableApplicant(accountId)
        require(applicant.graduationType != GraduationType.GED) {
            "middle school info is unavailable for GED applicants"
        }
        require(schoolCode.isNotBlank()) { "schoolCode is required" }
        require(schoolName.isNotBlank()) { "schoolName is required" }
        require(studentNumber.isNotBlank()) { "studentNumber is required" }
        require(schoolPhone.isNotBlank()) { "schoolPhone is required" }
        require(teacherName.isNotBlank()) { "teacherName is required" }

        applicant.middleSchoolInfo = MiddleSchoolInfo(
            schoolCode = schoolCode,
            schoolName = schoolName,
            studentNumber = studentNumber,
            schoolPhone = schoolPhone,
            teacherName = teacherName,
        )
        saveTouched(applicant)
    }

    fun updateIntroduction(accountId: Long?, introduction: String) {
        require(introduction.isNotBlank()) { "introduction is required" }
        require(introduction.length <= MAX_ESSAY_LENGTH) { "introduction is too long" }

        val applicant = getWritableApplicant(accountId)
        applicant.introduction = introduction
        saveTouched(applicant)
    }

    fun updateStudyPlan(accountId: Long?, studyPlan: String) {
        require(studyPlan.isNotBlank()) { "studyPlan is required" }
        require(studyPlan.length <= MAX_ESSAY_LENGTH) { "studyPlan is too long" }

        val applicant = getWritableApplicant(accountId)
        applicant.studyPlan = studyPlan
        saveTouched(applicant)
    }

    fun submit(accountId: Long?) {
        val applicant = getWritableApplicant(accountId)
        require(applicant.admissionType != null) { "admission type is required" }
        require(!applicant.name.isNullOrBlank()) { "personal info is required" }
        require(!applicant.guardianName.isNullOrBlank()) { "family info is required" }
        require(!applicant.introduction.isNullOrBlank()) { "introduction is required" }
        require(!applicant.studyPlan.isNullOrBlank()) { "studyPlan is required" }
        markSubmitted(applicant)
    }

    /**
     * 학생이 원서를 쓰는 요청은 원서 접수 기간에만 받는다.
     * 취소(identity 경유)와 도착 처리(admin)는 기간과 상관없어 [getApplicantByAccountId] 를 쓴다.
     */
    private fun getWritableApplicant(accountId: Long?): Applicant {
        applicationPeriod.requireOpen()
        return getApplicantByAccountId(accountId)
    }

    private fun getApplicantByAccountId(accountId: Long?): Applicant {
        val id = requireAccountId(accountId)
        return applicantRepository.findByAccountId(id)
            ?: throw ApplicantNotFoundException(id)
    }

    private fun requireAccountId(accountId: Long?): Long =
        accountId ?: throw AuthenticationRequiredException()

    private fun markSubmitted(applicant: Applicant) {
        applicant.status = ApplicantStatus.SUBMITTED
        applicant.statusVersion += 1
        applicant.submittedAt = nowUtc()
        publishStatus(saveTouched(applicant))
    }

    private fun saveTouched(applicant: Applicant): Applicant {
        applicant.touch()
        return applicantRepository.save(applicant)
    }

    private fun Applicant.toSnapshot(): ApplicationSnapshotResult = ApplicationSnapshotResult(
        accountId = accountId,
        applicantStatus = status,
        submittedAt = submittedAt,
        updatedAt = updatedAt,
        passStatus = passStatus,
        announcedAt = announcedAt,
    )

    companion object {
        private const val NEW_APPLICANT_ID = 0L
        /** 원서를 낸 것으로 보는 상태. 작성 중(DRAFT)과 취소(CANCELED)는 지원자가 아니다. */
        private val APPLIED_STATUSES = setOf(
            ApplicantStatus.SUBMITTED,
            ApplicantStatus.ARRIVAL,
            ApplicantStatus.REVIEWING,
            ApplicantStatus.COMPLETED,
        )
        /** 서식 1 의 직전·직전전 학기 후보. ScoreCalculator 의 반영 학기 순서와 같다. */
        private val PREVIOUS_SEMESTERS = listOf(
            SchoolSemester.SECOND_GRADE_SECOND_SEMESTER,
            SchoolSemester.SECOND_GRADE_FIRST_SEMESTER,
            SchoolSemester.FIRST_GRADE_SECOND_SEMESTER,
            SchoolSemester.FIRST_GRADE_FIRST_SEMESTER,
        )
        private val STUDENT_NUMBER = Regex("^\\d(\\d{2})\\d{2}$")
        private val PHONE_NUMBER_REGEX = Regex("^010-\\d{4}-\\d{4}$")
        private const val MAX_ESSAY_LENGTH = 1600
        // applicants.photo_file_id 컬럼 길이. document 증명사진 ID 는 photo_ 와 32자 임의값이다.
        private const val MAX_PHOTO_FILE_ID_LENGTH = 64
    }
}

/** 자유학기처럼 반영할 과목이 하나도 없는 학기는 서식 1 의 열로 쓰지 않는다. */
private fun Map<SchoolSemester, SubjectGrades>.reflected(semester: SchoolSemester): SubjectGrades? =
    this[semester]?.takeIf { it.hasReflectedSubject() }

private fun SubjectGrades.hasReflectedSubject(): Boolean = listOf(
    koreanGrade, societyGrade, historyGrade, mathGrade, scienceGrade, technologyGrade, englishGrade,
).any { it != SubjectGrade.X }
