package hs.kr.entrydsm.admin.adapterout.grpc

import hs.kr.entrydsm.admin.adapterout.entity.ScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ApplicantExportProjectionJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportEventJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantExportProjectionJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ScreeningJpaRepository
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
import hs.kr.entrydsm.admin.domain.model.Page
import hs.kr.entrydsm.admin.domain.model.PageRequest
import hs.kr.entrydsm.admin.domain.model.SemesterGrades
import hs.kr.entrydsm.admin.domain.port.out.ApplicantRepository
import hs.kr.entrydsm.admin.domain.port.out.ApplicantArrivalPort
import hs.kr.entrydsm.admin.domain.port.out.ApplicantDeletionPort
import hs.kr.entrydsm.application.grpc.AdmissionType as GrpcAdmissionType
import hs.kr.entrydsm.application.grpc.ApplicantResponse
import hs.kr.entrydsm.application.grpc.ApplicationServiceGrpc
import hs.kr.entrydsm.application.grpc.ApplicationFormResponse
import hs.kr.entrydsm.application.grpc.BatchGetApplicationFormsRequest
import hs.kr.entrydsm.application.grpc.GetApplicantRequest
import hs.kr.entrydsm.application.grpc.DeleteApplicantRequest
import hs.kr.entrydsm.application.grpc.GetApplicationFormRequest
import hs.kr.entrydsm.application.grpc.GraduationType as GrpcGraduationType
import hs.kr.entrydsm.application.grpc.Gender as GrpcGender
import hs.kr.entrydsm.application.grpc.ListApplicantsRequest
import hs.kr.entrydsm.application.grpc.UpdateApplicantArrivalRequest
import hs.kr.entrydsm.application.grpc.UpdateExamineeNumberRequest
import hs.kr.entrydsm.application.grpc.Region as GrpcRegion
import hs.kr.entrydsm.application.grpc.SpecialAdmissionType as GrpcSpecialAdmissionType
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.springframework.stereotype.Component

/**
 * 지원자를 application gRPC 로 읽고 admin 의 전형 정보를 덧붙입니다.
 *
 * 원서 내용은 application 이 갖고 admin 은 `screening` 행만 가지므로, 두 곳을 합쳐야
 * 지원자 한 명이 됩니다. 전형 정보가 없는 지원자는 미도착·수험 번호 없음·`PENDING` 입니다.
 *
 * ponytail: 목록 필터·정렬·페이징을 메모리에서 한다. 한 회차 수천 명 규모라 충분하다
 * (통계 집계도 같은 이유로 전체를 읽는다). 키워드가 이름(application)과 수험 번호(admin)를
 * 함께 보는 탓에 어차피 합친 뒤에만 걸 수 있다. 만 단위로 커지면 application 에 필터·페이징
 * RPC 를 더해 그쪽으로 넘긴다.
 */
@Component
class GrpcApplicantDataAdapter(
    private val grpc: ApplicationGrpcChannel,
    private val screeningJpaRepository: ScreeningJpaRepository,
    private val exportEventRepository: ApplicantExportEventJpaRepository,
    private val exportProjectionRepository: ApplicantExportProjectionJpaRepository,
) : ApplicantRepository, ApplicantArrivalPort, ApplicantDeletionPort {
    private val stub = ApplicationServiceGrpc.newBlockingStub(grpc.channel)

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

        return call { listStub().listApplicants(ListApplicantsRequest.getDefaultInstance()) }
            .applicantsList
            .map { it.toApplicant(screenings[it.applicantId]) }
            .filter { it.matches(filter) }
            .sortedBy { it.id }
    }

    override fun findById(applicantId: Long): Applicant? =
        getApplicant(applicantId)?.toApplicant(screeningJpaRepository.findById(applicantId).orElse(null))

    /**
     * 자기소개서·학업계획서는 원서 주인 계정으로 찾는 원서 내용(`GetApplicationForm`)에만 있다.
     * `ApplicantResponse` 에 얹으면 `ListApplicants` 가 제출 원서 전체의 본문을 한 메시지로 나르게 된다.
     * document 가 관리자 원서 출력에 쓰는 순서(지원자 → 주인 계정 → 원서 내용)와 같다.
     */
    override fun findDetailById(applicantId: Long): ApplicantDetail? {
        val response = getApplicant(applicantId) ?: return null
        val form = call {
            oneStub().getApplicationForm(GetApplicationFormRequest.newBuilder().setAccountId(response.userId).build())
        }

        return ApplicantDetail(
            applicant = response.toApplicant(screeningJpaRepository.findById(applicantId).orElse(null)),
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
        )
    }

    override fun findFirstPassRows(): List<FirstPassRow> {
        val firstPassIds = screeningJpaRepository.findAll()
            .filter { it.status == ApplicantStatus.FIRST_PASS }
            .mapTo(hashSetOf()) { it.applicantId }
        if (firstPassIds.isEmpty()) return emptyList()

        val applicants = call { listStub().listApplicants(ListApplicantsRequest.getDefaultInstance()) }
            .applicantsList
            .filter { it.applicantId in firstPassIds }
        if (applicants.isEmpty()) return emptyList()

        val forms = call {
            listStub().batchGetApplicationForms(
                BatchGetApplicationFormsRequest.newBuilder()
                    .addAllAccountId(applicants.map { it.userId })
                    .build(),
            )
        }.applicationsList.associateBy { it.userId }

        return applicants.mapNotNull { applicant ->
            forms[applicant.userId]?.toFirstPassRow()
        }.sortedBy { it.receiptNumber }
    }

    override fun findAdmissionFileRows(): List<FirstPassRow> {
        return exportProjectionRepository.findAll()
            .map { ApplicationFormResponse.parseFrom(it.payload).toFirstPassRow() }
            .sortedBy { it.receiptNumber }
    }

    override fun findApplicationChecklistRows(): List<FirstPassRow> = findAdmissionFileRows()

    override fun findFirstPassApplicants(): List<Applicant> {
        val screenings = screeningJpaRepository.findAll()
            .filter { it.status == ApplicantStatus.FIRST_PASS }
            .associateBy { it.applicantId }
        return exportProjectionRepository.findAllById(screenings.keys).map { projection ->
            val form = ApplicationFormResponse.parseFrom(projection.payload)
            Applicant(
                id = projection.applicantId,
                name = form.name.takeIf { form.hasName() },
                examineeNumber = screenings[projection.applicantId]?.examineeNumber,
                status = ApplicantStatus.FIRST_PASS,
            )
        }.sortedBy { it.id }
    }

    override fun syncExportProjection() {
        if (exportProjectionRepository.count() == 0L) {
            val applicants = call { listStub().listApplicants(ListApplicantsRequest.getDefaultInstance()) }.applicantsList
            if (applicants.isNotEmpty()) {
                val initial = call {
                    listStub().batchGetApplicationForms(
                        BatchGetApplicationFormsRequest.newBuilder()
                            .addAllAccountId(applicants.map { it.userId })
                            .build(),
                    )
                }.applicationsList
                exportProjectionRepository.saveAll(initial.map {
                    ApplicantExportProjectionJpaEntity(it.applicantId, it.userId, it.toByteArray())
                })
            }
        }
        val events = exportEventRepository.findAllByProcessedFalse()
        val latestEvents = events.groupBy { it.applicantId }.values.map { applicantEvents ->
            applicantEvents.maxBy { it.eventVersion }
        }
        val removedStatuses = setOf("APPLICANT_STATUS_NONE", "APPLICANT_STATUS_DRAFT", "APPLICANT_STATUS_CANCELED")
        latestEvents.filter { it.applicantStatus in removedStatuses }
            .forEach { exportProjectionRepository.deleteById(it.applicantId) }

        val active = latestEvents.filterNot { it.applicantStatus in removedStatuses }
        if (active.isNotEmpty()) {
            val forms = call {
                listStub().batchGetApplicationForms(
                    BatchGetApplicationFormsRequest.newBuilder().addAllAccountId(active.map { it.accountId }.distinct()).build(),
                )
            }.applicationsList
            exportProjectionRepository.saveAll(forms.map {
                ApplicantExportProjectionJpaEntity(it.applicantId, it.userId, it.toByteArray())
            })
        }
        exportEventRepository.saveAll(events.onEach { it.processed = true })
    }

    private fun getApplicant(applicantId: Long): ApplicantResponse? =
        try {
            oneStub().getApplicant(GetApplicantRequest.newBuilder().setApplicantId(applicantId).build())
        } catch (exception: StatusRuntimeException) {
            // 0 이하 id 는 application 이 INVALID_ARGUMENT 로 거절한다. 없는 지원자와 같다.
            if (exception.status.code in NO_APPLICANT) null else throw exception.toApplicationException()
        }

    override fun save(applicant: Applicant): Applicant {
        screeningJpaRepository.save(applicant.toScreening())
        syncExamineeNumber(applicant)
        return applicant
    }

    override fun saveAll(applicants: List<Applicant>): List<Applicant> {
        screeningJpaRepository.saveAll(applicants.map { it.toScreening() })
        applicants.forEach(::syncExamineeNumber)
        return applicants
    }

    private fun syncExamineeNumber(applicant: Applicant) {
        val number = applicant.examineeNumber ?: return
        call {
            oneStub().updateExamineeNumber(
                UpdateExamineeNumberRequest.newBuilder()
                    .setApplicantId(applicant.id)
                    .setExamineeNumber(number)
                    .build(),
            )
        }
    }

    override fun deleteById(applicantId: Long) {
        screeningJpaRepository.deleteById(applicantId)
    }

    override fun delete(applicantId: Long) {
        call {
            oneStub().deleteApplicant(DeleteApplicantRequest.newBuilder().setApplicantId(applicantId).build())
        }
    }

    override fun update(applicantId: Long, isArrived: Boolean) {
        call {
            oneStub().updateApplicantArrival(
                UpdateApplicantArrivalRequest.newBuilder()
                    .setApplicantId(applicantId)
                    .setIsArrived(isArrived)
                    .build(),
            )
        }
    }

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

    private fun oneStub() = stub.withDeadlineAfter(grpc.deadlineMs, TimeUnit.MILLISECONDS)

    private fun listStub() = stub.withDeadlineAfter(grpc.listDeadlineMs, TimeUnit.MILLISECONDS)

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (exception: StatusRuntimeException) {
            throw exception.toApplicationException()
        }

    private fun StatusRuntimeException.toApplicationException() =
        toAdminException(
            notFound = ErrorCode.APPLICANT_NOT_FOUND,
            unavailable = ErrorCode.APPLICATION_SERVICE_UNAVAILABLE,
            failedPrecondition = ErrorCode.INVALID_STATUS_TRANSITION,
        )

    private companion object {
        val NO_APPLICANT = setOf(Status.Code.NOT_FOUND, Status.Code.INVALID_ARGUMENT)
    }
}
