package hs.kr.entrydsm.application.administration.adapterout.persistence

import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.application.administration.adapterout.entity.ScreeningJpaEntity
import org.springframework.transaction.annotation.Transactional
import hs.kr.entrydsm.application.administration.adapterout.repository.ScreeningJpaRepository
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.GraduationStatus
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.ApplicantScore
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.FirstPassRow
import hs.kr.entrydsm.admin.domain.model.GedScores
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.model.SemesterGrades
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantDeletionPort
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import java.time.Instant
import java.time.LocalDate
import org.springframework.stereotype.Component

import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.`in`.command.UpdateApplicantArrivalCommand
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import hs.kr.entrydsm.application.adapterout.repository.ScreeningResultEventHandler
import hs.kr.entrydsm.application.grpc.PassStatus
import hs.kr.entrydsm.application.grpcmapping.toGrpcApplicant
import hs.kr.entrydsm.application.grpcmapping.toGrpcForm

/** 관리자 조회와 전형 변경은 원본 원서와 같은 서비스의 트랜잭션에서 처리한다. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Component
class LocalApplicantDataAdapter(
    private val applicationPort: ApplicationPort,
    private val screeningJpaRepository: ScreeningJpaRepository,
    private val resultHandler: ScreeningResultEventHandler,
    private val lockedApplicants: hs.kr.entrydsm.application.adapterout.repository.ApplicantJpaRepository,
) : ApplicantRepository, ApplicantArrivalPort, ApplicantDeletionPort {
    override fun search(filter: ApplicantFilter, pageRequest: PageRequest): Page<Applicant> {
        val matched = findAll(filter)

        return Page(
            items = matched
                .drop((pageRequest.normalizedPage - 1) * pageRequest.normalizedSize)
                .take(pageRequest.normalizedSize),
            page = pageRequest.normalizedPage,
            size = pageRequest.normalizedSize,
            totalElements = matched.size.toLong(),
        )
    }

    override fun findAll(filter: ApplicantFilter): List<Applicant> {
        val screenings = screeningJpaRepository.findAll().associateBy { it.applicantId }
        return source { applicationPort.listApplicants() }
            .map { it.toGrpcApplicant().toApplicant(screenings[it.applicantId]).copy(applicationVersion = it.statusVersion) }
            .filter { it.matches(filter) }.sortedBy { it.id }
    }

    override fun findById(applicantId: Long): Applicant? =
        applicantId.takeIf { it > 0 }?.let {
            source(it) { applicationPort.findApplicant(it) }?.let { original ->
                original.toGrpcApplicant().toApplicant(screeningJpaRepository.findById(it).orElse(null))
                    .copy(applicationVersion = original.statusVersion)
            }
        }

    override fun findDetailById(applicantId: Long): ApplicantDetail? {
        if (applicantId <= 0) return null
        val applicant = source(applicantId) { applicationPort.findApplicant(applicantId) } ?: return null
        val form = readForm(applicantId, applicant.accountId)
        val response = form.toApplicantResponse()
        return ApplicantDetail(
            version = form.statusVersion,
            applicant = response.toApplicant(screeningJpaRepository.findById(applicantId).orElse(null)).copy(applicationVersion = form.statusVersion),
            photoFileId = form.photoFileId.takeIf { form.hasPhotoFileId() },
            introduction = form.introduction.takeIf { form.hasIntroduction() },
            studyPlan = form.studyPlan.takeIf { form.hasStudyPlan() },
            score = form.takeIf { it.hasTotalScore() }?.let {
                ApplicantScore(
                    subjectScore = it.subjectScore,
                    attendanceScore = it.attendanceScore,
                    volunteerScore = it.volunteerScore,
                    additionalScore = it.additionalScore,
                    totalScore = it.totalScore,
                )
            },
            gedScores = form.gedScores.takeIf { form.hasGedScores() }?.let {
                GedScores(
                    korean = it.korean,
                    society = it.society,
                    history = it.history,
                    math = it.math,
                    science = it.science,
                    technology = it.technology,
                    english = it.english,
                )
            },
        )
    }

    override fun findFirstPassRows(): List<FirstPassRow> {
        val ids = screeningJpaRepository.findAll()
            .filter { it.status == ApplicantStatus.FIRST_PASS }.mapTo(hashSetOf()) { it.applicantId }
        return readRows(ids)
    }

    override fun findAdmissionFileRows(): List<FirstPassRow> = readRows()

    override fun findApplicationChecklistRows(): List<FirstPassRow> = readRows()

    override fun findFirstPassApplicants(): List<Applicant> =
        findAll(ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS)))

    override fun hasFirstPassApplicants(): Boolean = findFirstPassApplicants().isNotEmpty()

    private fun readRows(ids: Set<Long>? = null): List<FirstPassRow> {
        val applicants = source { applicationPort.listApplicants() }.filter { ids == null || it.applicantId in ids }
        val failedIds = mutableListOf<Long>()
        val codes = mutableSetOf<ErrorCode>()
        val rows = applicants.mapNotNull { applicant ->
            try {
                readForm(applicant.applicantId, applicant.accountId).toFirstPassRow()
            } catch (exception: AdminDomainException) {
                failedIds += applicant.applicantId
                codes += exception.errorCode
                null
            }
        }
        if (failedIds.isNotEmpty()) throw AdminDomainException(
            codes.singleOrNull() ?: ErrorCode.APPLICATION_SYNC_FAILED,
            failedCount = failedIds.size, totalCount = applicants.size, targetIds = failedIds,
        )
        return rows.sortedBy { it.receiptNumber }
    }

    private fun readForm(applicantId: Long, accountId: Long): ApplicationFormResponse = try {
        val form = applicationPort.findApplicationForm(accountId)
            ?: throw AdminDomainException(ErrorCode.APPLICATION_FORM_NOT_FOUND, targetIds = listOf(applicantId))
        if (form.applicantId != applicantId || form.accountId != accountId) {
            throw AdminDomainException(ErrorCode.APPLICATION_FORM_INVALID, targetIds = listOf(applicantId))
        }
        form.requireEvaluatedScore()
        form.toGrpcForm()
    } catch (exception: EvaluationValidationException) {
        throw AdminDomainException(ErrorCode.APPLICATION_SCORE_INVALID, exception, targetIds = listOf(applicantId))
    } catch (exception: IllegalArgumentException) {
        throw AdminDomainException(ErrorCode.APPLICATION_FORM_INVALID, exception, targetIds = listOf(applicantId))
    }

    @Transactional
    override fun save(applicant: Applicant): Applicant = source(applicant.id) {
        val previous = screeningJpaRepository.findForUpdate(applicant.id)?.status ?: ApplicantStatus.PENDING
        val original = lockedApplicants.findForUpdate(applicant.id)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
        if (original.statusVersion != applicant.applicationVersion) {
            throw AdminDomainException(ErrorCode.APPLICATION_VERSION_CONFLICT)
        }
        if (previous != applicant.status) {
            resultHandler.applyLocal(applicant.id, when (applicant.status) {
                ApplicantStatus.PENDING -> PassStatus.PASS_STATUS_NOT_ANNOUNCED
                ApplicantStatus.FIRST_PASS -> PassStatus.PASS_STATUS_FIRST_PASSED
                ApplicantStatus.FIRST_FAIL -> PassStatus.PASS_STATUS_FIRST_FAILED
                ApplicantStatus.FINAL_PASS -> PassStatus.PASS_STATUS_FINAL_PASSED
                ApplicantStatus.FINAL_FAIL -> PassStatus.PASS_STATUS_FINAL_FAILED
            }, applicant.updatedAt ?: Instant.now())
        }
        applicant.examineeNumber?.let { applicationPort.updateExamineeNumber(applicant.id, it) }
        screeningJpaRepository.save(applicant.toScreening())
        applicant
    }

    @Transactional
    override fun saveAll(applicants: List<Applicant>): List<Applicant> {
        applicants.sortedBy { it.id }.forEach { save(it) }
        return applicants
    }

    override fun deleteById(applicantId: Long) = screeningJpaRepository.deleteById(applicantId)

    override fun delete(applicantId: Long) {
        try { applicationPort.deleteApplicant(applicantId) }
        catch (exception: ApplicantNotFoundException) {
            throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND, exception)
        }
    }

    override fun update(applicantId: Long, isArrived: Boolean) {
        source(applicantId, ErrorCode.INVALID_STATUS_TRANSITION) {
            applicationPort.updateArrival(UpdateApplicantArrivalCommand(applicantId, isArrived))
        }
    }

    private inline fun <T> source(targetId: Long? = null,
        invalidArgument: ErrorCode = ErrorCode.APPLICATION_FORM_INVALID, action: () -> T): T = try {
        action()
    } catch (exception: EvaluationValidationException) {
        throw AdminDomainException(ErrorCode.APPLICATION_SCORE_INVALID, exception, targetIds = listOfNotNull(targetId))
    } catch (exception: ApplicantNotFoundException) {
        throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND, exception, targetIds = listOfNotNull(targetId))
    } catch (exception: hs.kr.entrydsm.application.application.exception.ScreeningResultChangeNotAllowedException) {
        throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION, exception, targetIds = listOfNotNull(targetId))
    } catch (exception: IllegalArgumentException) {
        throw AdminDomainException(invalidArgument, exception, targetIds = listOfNotNull(targetId))
    }

    private fun ApplicationFormResponse.toApplicantResponse(): ApplicantResponse = ApplicantResponse.newBuilder()
        .setApplicantId(applicantId).setUserId(userId).setRegion(region).setAdmissionType(admissionType)
        .setGraduationType(graduationType).setGender(gender)
        .also { builder ->
            if (hasName()) builder.setName(name)
            if (hasPhoneNumber()) builder.setPhoneNumber(phoneNumber)
            if (hasBirthdate()) builder.setBirthdate(birthdate)
            if (hasAddressBase()) builder.setAddress(addressBase) else if (hasAddress()) builder.setAddress(address)
            if (hasMiddleSchool()) builder.setSchoolName(middleSchool.name)
            if (hasTotalScore()) builder.setTotalScore(totalScore)
            if (hasSubmittedAtEpochMillis()) builder.setSubmittedAtEpochMillis(submittedAtEpochMillis)
        }.build()

    private fun ApplicantResponse.toApplicant(screening: ScreeningJpaEntity?) = Applicant(
        id = applicantId,
        name = name.takeIf { hasName() },
        // application 이 ISO-8601 로 넣는다. 어긋난 값 하나가 목록 전체를 막지 않게 빈 값으로 둔다.
        birthDate = birthdate.takeIf { hasBirthdate() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        phoneNumber = phoneNumber.takeIf { hasPhoneNumber() },
        region = when (region) {
            GrpcRegion.REGION_DAEJEON -> Region.DAEJEON
            GrpcRegion.REGION_NATIONAL -> Region.NATIONWIDE
            else -> null
        },
        admissionType = when (admissionType) {
            GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> AdmissionType.GENERAL
            GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> AdmissionType.MEISTER
            GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> AdmissionType.SOCIAL
            else -> null
        },
        graduationStatus = when (graduationType) {
            GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE -> GraduationStatus.EXPECTED
            GrpcGraduationType.GRADUATION_TYPE_GRADUATED -> GraduationStatus.GRADUATED
            GrpcGraduationType.GRADUATION_TYPE_GED -> GraduationStatus.GED
            else -> null
        },
        schoolName = schoolName.takeIf { hasSchoolName() },
        totalScore = totalScore.takeIf { hasTotalScore() },
        submittedAt = submittedAtEpochMillis.takeIf { hasSubmittedAtEpochMillis() }?.let(Instant::ofEpochMilli),
        examineeNumber = screening?.examineeNumber,
        isArrived = screening?.isArrived ?: false,
        status = screening?.status ?: ApplicantStatus.PENDING,
        arrivedAt = screening?.arrivedAt,
        updatedAt = screening?.updatedAt,
        gender = when (gender) {
            GrpcGender.GENDER_MALE -> Gender.MALE
            GrpcGender.GENDER_FEMALE -> Gender.FEMALE
            else -> null
        },
        address = address.takeIf { hasAddress() },
    )

    private fun ApplicationFormResponse.toFirstPassRow(): FirstPassRow {
        val thirdSecond = thirdGradeSecondSemester.takeIf { hasThirdGradeSecondSemester() }.toGrades()
        val thirdFirst = thirdGradeFirstSemester.takeIf { hasThirdGradeFirstSemester() }.toGrades()
        val previous = previousSemester.takeIf { hasPreviousSemester() }.toGrades()
        val secondPrevious = secondPreviousSemester.takeIf { hasSecondPreviousSemester() }.toGrades()
        val codes = listOf(
            admissionTypeCode.takeIf { hasAdmissionTypeCode() },
            regionCode.takeIf { hasRegionCode() },
            specialAdmissionTypeCode.takeIf { hasSpecialAdmissionTypeCode() },
        )

        return FirstPassRow(
            combinedCode = codes.takeIf { it.all { code -> !code.isNullOrBlank() } }
                ?.joinToString(""),
            receiptNumber = applicantId.toString().padStart(4, '0'),
            admissionType = when (admissionType) {
                GrpcAdmissionType.ADMISSION_TYPE_REGULAR -> "일반전형"
                GrpcAdmissionType.ADMISSION_TYPE_MEISTER -> "마이스터전형"
                GrpcAdmissionType.ADMISSION_TYPE_SOCIAL -> "사회통합전형"
                else -> null
            },
            region = when (region) {
                GrpcRegion.REGION_DAEJEON -> "대전"
                GrpcRegion.REGION_NATIONAL -> "전국"
                else -> null
            },
            specialAdmissionType = when (specialAdmissionType) {
                GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NONE -> "해당없음"
                GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_NATIONAL_MERIT -> "국가유공자"
                GrpcSpecialAdmissionType.SPECIAL_ADMISSION_TYPE_SPECIAL_ADMISSION -> "특례입학"
                else -> null
            },
            name = name.takeIf { hasName() },
            birthDate = birthdate.takeIf { hasBirthdate() },
            address = address.takeIf { hasAddress() },
            phoneNumber = phoneNumber.takeIf { hasPhoneNumber() },
            gender = when (gender) {
                GrpcGender.GENDER_MALE -> "남"
                GrpcGender.GENDER_FEMALE -> "여"
                else -> null
            },
            graduationStatus = when (graduationType) {
                GrpcGraduationType.GRADUATION_TYPE_PROSPECTIVE -> "졸업예정"
                GrpcGraduationType.GRADUATION_TYPE_GRADUATED -> "졸업"
                GrpcGraduationType.GRADUATION_TYPE_GED -> "검정고시"
                else -> null
            },
            graduationYear = graduationDate.takeIf { hasGraduationDate() }?.take(4),
            schoolName = middleSchool.takeIf { hasMiddleSchool() }?.name,
            classNumber = classNumber.takeIf { hasClassNumber() },
            studentNumber = studentNumber.takeIf { hasStudentNumber() },
            guardianName = guardianName.takeIf { hasGuardianName() },
            guardianPhoneNumber = guardianPhoneNumber.takeIf { hasGuardianPhoneNumber() },
            thirdGradeSecondSemester = thirdSecond,
            thirdGradeFirstSemester = thirdFirst,
            previousSemester = previous,
            secondPreviousSemester = secondPrevious,
            thirdGradeTotal = listOfNotNull(thirdSecond.total, thirdFirst.total).takeIf { it.isNotEmpty() }?.sum(),
            previousSemesterTotal = previous.total,
            secondPreviousSemesterTotal = secondPrevious.total,
            subjectScore = subjectScore.takeIf { hasSubjectScore() },
            volunteerTime = academicRecord.takeIf { hasAcademicRecord() }?.volunteerTime,
            volunteerScore = volunteerScore.takeIf { hasVolunteerScore() },
            absentCount = academicRecord.takeIf { hasAcademicRecord() }?.absentCount,
            lateCount = academicRecord.takeIf { hasAcademicRecord() }?.lateCount,
            earlyLeaveCount = academicRecord.takeIf { hasAcademicRecord() }?.earlyLeaveCount,
            classAbsenceCount = academicRecord.takeIf { hasAcademicRecord() }?.classAbsenceCount,
            attendanceScore = attendanceScore.takeIf { hasAttendanceScore() },
            awarded = academicRecord.takeIf { hasAcademicRecord() }?.dsmAlgorithmAwarded,
            certified = academicRecord.takeIf { hasAcademicRecord() }?.programmingCertified,
            additionalScore = additionalScore.takeIf { hasAdditionalScore() },
            totalScore = totalScore.takeIf { hasTotalScore() },
            admissionTypeCode = codes[0],
            regionCode = codes[1],
            specialAdmissionTypeCode = codes[2],
            gedAverage = gedAverage.takeIf { hasGedAverage() },
        )
    }

    private fun hs.kr.entrydsm.application.grpc.SemesterGrades?.toGrades(): SemesterGrades =
        SemesterGrades(
            korean = this?.korean?.takeIf(String::isNotBlank),
            society = this?.society?.takeIf(String::isNotBlank),
            history = this?.history?.takeIf(String::isNotBlank),
            math = this?.math?.takeIf(String::isNotBlank),
            science = this?.science?.takeIf(String::isNotBlank),
            technology = this?.technology?.takeIf(String::isNotBlank),
            english = this?.english?.takeIf(String::isNotBlank),
        )

    private fun Applicant.toScreening() = ScreeningJpaEntity(
        applicantId = id,
        examineeNumber = examineeNumber,
        isArrived = isArrived,
        status = status,
        arrivedAt = arrivedAt,
        updatedAt = updatedAt,
    )

    private fun Applicant.matches(filter: ApplicantFilter): Boolean {
        val keyword = filter.keyword?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

        return (
            keyword == null ||
                name?.lowercase()?.contains(keyword) == true ||
                examineeNumber?.lowercase()?.contains(keyword) == true
            ) &&
            (filter.regions.isEmpty() || region in filter.regions) &&
            (filter.admissionTypes.isEmpty() || admissionType in filter.admissionTypes) &&
            (filter.graduationStatuses.isEmpty() || graduationStatus in filter.graduationStatuses) &&
            (filter.isArrived == null || isArrived == filter.isArrived) &&
            (filter.statuses.isEmpty() || status in filter.statuses)
    }

}
