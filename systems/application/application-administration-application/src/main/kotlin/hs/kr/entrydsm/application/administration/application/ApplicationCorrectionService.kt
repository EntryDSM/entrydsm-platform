package hs.kr.entrydsm.application.administration.application

import hs.kr.entrydsm.admin.domain.command.*
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.port.`in`.*
import hs.kr.entrydsm.admin.domain.port.out.*
import hs.kr.entrydsm.application.application.exception.ApplicationValidationException
import hs.kr.entrydsm.application.application.port.`in`.ApplicationPort
import hs.kr.entrydsm.application.application.port.out.AccountPhoneValidator
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusChanged
import hs.kr.entrydsm.application.application.port.out.ApplicantStatusEventOutbox
import hs.kr.entrydsm.application.application.service.ApplicationFormEditor
import hs.kr.entrydsm.application.domain.enum.*
import hs.kr.entrydsm.application.domain.model.*
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@Service
@Transactional
class ApplicationCorrectionService(
    private val applicants: ApplicantRepository,
    private val states: ApplicationCorrectionStatePort,
    private val audits: ApplicationCorrectionAuditRepository,
    private val application: ApplicationPort,
    private val outbox: ApplicantStatusEventOutbox,
    private val schools: hs.kr.entrydsm.application.application.port.out.MiddleSchoolRepository,
    phoneValidator: AccountPhoneValidator,
) : CorrectApplicationUseCase {
    private val editor = ApplicationFormEditor(phoneValidator)

    override fun correct(command: CorrectApplicationCommand, editorId: String): ApplicationCorrectionResult {
        if (editorId.isBlank()) throw AdminDomainException(ErrorCode.AUTH_UNAUTHORIZED)
        require(command.applicantId > 0)
        require(command.reason.isNotBlank() && command.reason.length <= 500)
        val state = states.lock(command.applicantId)
        val original = applicants.findForUpdate(command.applicantId)
            ?: throw AdminDomainException(ErrorCode.APPLICANT_NOT_FOUND)
        if (original.status !in setOf(ApplicantStatus.SUBMITTED, ApplicantStatus.ARRIVAL) ||
            original.passResultType != null || state.hasResult) {
            throw AdminDomainException(ErrorCode.INVALID_STATUS_TRANSITION)
        }
        // 원본과 분리한 성적 기록에서 검증해 실패한 요청이 원본 객체를 바꾸지 않도록 한다.
        val corrected = original.copy(academicRecord = original.academicRecord?.let {
            it.copy(subjectGrades = LinkedHashMap(it.subjectGrades))
        })
        val fields = try {
            applyChanges(corrected, command.changes)
            changedFields(original, corrected).also { require(it.isNotEmpty()) }
        } catch (exception: ApplicationValidationException) {
            throw AdminDomainException(ErrorCode.INVALID_APPLICATION_CORRECTION)
        } catch (exception: IllegalArgumentException) {
            throw AdminDomainException(ErrorCode.INVALID_APPLICATION_CORRECTION)
        }
        // 전형·지역·주소가 바뀌어도 이미 발급된 수험번호는 원본 값을 유지한다.
        if (fields.any { it in setOf("admissionType", "graduationType", "academicRecord") }) {
            corrected.totalScore = try { ScoreCalculator().calculate(corrected) }
            catch (exception: IllegalArgumentException) {
                throw AdminDomainException(ErrorCode.INVALID_APPLICATION_CORRECTION)
            }
            corrected.totalScoreUpdatedAt = nowUtc()
        } else {
            corrected.totalScore = original.totalScore
            corrected.totalScoreUpdatedAt = original.totalScoreUpdatedAt
        }
        corrected.statusVersion = Math.addExact(original.statusVersion, 1)
        corrected.touch()
        val saved = applicants.save(corrected)
        audits.add(ApplicationCorrectionAudit(saved.id, editorId, command.reason.trim(), fields,
            saved.statusVersion, saved.updatedAt.toInstant(ZoneOffset.UTC)))
        outbox.add(ApplicantStatusChanged(
            accountId = saved.accountId, applicantId = saved.id, status = saved.status,
            occurredAt = saved.updatedAt, version = saved.statusVersion, submittedAt = saved.submittedAt,
            passStatus = saved.passStatus, passResultType = saved.passResultType, announcedAt = saved.announcedAt,
            applicationForm = requireNotNull(application.findApplicationForm(saved.accountId)),
        ))
        return ApplicationCorrectionResult(saved.id, saved.statusVersion)
    }

    private fun applyChanges(a: Applicant, c: ApplicationFormChanges) {
        listOf(c.name, c.guardianName, c.addressBase, c.addressDetail, c.zipCode).filterNotNull()
            .forEach { require(it.toByteArray(Charsets.UTF_8).size <= 300) }
        c.guardianRelation?.let { require(it.isNotBlank() && it.length <= 10) }
        if (listOf(c.admissionType, c.region, c.graduationType, c.graduationDate).any { it != null }) {
            val graduation = c.graduationType?.let(GraduationType::valueOf) ?: requireNotNull(a.graduationType)
            require(graduation != GraduationType.GED || c.graduationDate == null)
            if (graduation != a.graduationType &&
                (graduation == GraduationType.GED || a.graduationType == GraduationType.GED)) {
                require(c.academicRecord != null) // 학력 전환에 맞는 성적 전체를 같은 요청에서 검증한다.
            }
            editor.updateType(a, c.admissionType?.let {
                AdmissionType.valueOf(if (it == "GENERAL") "REGULAR" else it)
            } ?: requireNotNull(a.admissionType), c.region?.let {
                Region.valueOf(if (it == "NATIONWIDE") "NATIONAL" else it)
            } ?: requireNotNull(a.region),
                graduation, if (graduation == GraduationType.GED) null else c.graduationDate?.let(YearMonth::parse) ?: a.graduationDate)
        }
        if (listOf(c.photoFileId, c.name, c.phoneNumber, c.gender, c.birthdate, c.specialAdmissionType).any { it != null }) {
            editor.updatePersonal(a, c.photoFileId ?: requireNotNull(a.photoFileId), c.name ?: requireNotNull(a.name),
                c.phoneNumber ?: requireNotNull(a.phoneNumber), c.gender?.let(Gender::valueOf) ?: requireNotNull(a.gender),
                c.birthdate?.let(LocalDate::parse) ?: requireNotNull(a.birthdate),
                c.specialAdmissionType?.let(SpecialAdmissionType::valueOf) ?: a.specialAdmissionType)
        }
        if (listOf(c.guardianName, c.guardianPhoneNumber, c.guardianGender, c.guardianRelation,
                c.zipCode, c.addressBase, c.addressDetail).any { it != null }) {
            editor.updateFamily(a, c.guardianName ?: requireNotNull(a.guardianName),
                c.guardianPhoneNumber ?: requireNotNull(a.guardianPhoneNumber),
                c.guardianGender?.let(Gender::valueOf) ?: requireNotNull(a.guardianGender),
                c.guardianRelation ?: requireNotNull(a.guardianRelation), c.zipCode ?: requireNotNull(a.zipCode),
                c.addressBase ?: requireNotNull(a.addressBase), c.addressDetail ?: requireNotNull(a.addressDetail))
        }
        c.school?.let {
            require(it.schoolCode.length <= 20 && schools.existsByCode(it.schoolCode))
            require(it.schoolName.length <= 50 && it.schoolPhone.length <= 16 && it.teacherName.length <= 20)
            editor.updateMiddleSchool(a, it.schoolCode, it.schoolName, it.studentNumber, it.schoolPhone, it.teacherName)
        }
        c.introduction?.let { editor.updateIntroduction(a, it) }
        c.studyPlan?.let { editor.updateStudyPlan(a, it) }
        c.academicRecord?.let { record ->
            require(a.graduationType != null)
            require(if (a.graduationType == GraduationType.GED) record.subjectGrades.isEmpty() && record.gedScores != null
                else record.gedScores == null && record.subjectGrades.isNotEmpty())
            a.academicRecord = AcademicRecord(requireNotNull(record.absentCount), requireNotNull(record.lateCount), requireNotNull(record.earlyLeaveCount),
                requireNotNull(record.classAbsenceCount), requireNotNull(record.volunteerTime), requireNotNull(record.dsmAlgorithmAwarded), requireNotNull(record.programmingCertified),
                record.subjectGrades.mapKeys { SchoolSemester.valueOf(it.key) }.mapValues { (_, g) ->
                    SubjectGrades(SubjectGrade.valueOf(g.korean), SubjectGrade.valueOf(g.math), SubjectGrade.valueOf(g.english),
                        SubjectGrade.valueOf(g.science), SubjectGrade.valueOf(g.society), SubjectGrade.valueOf(g.technology), SubjectGrade.valueOf(g.history))
                }.toMutableMap(), record.gedScores?.let { g -> GedScores(requireNotNull(g.korean), requireNotNull(g.math), requireNotNull(g.english),
                    requireNotNull(g.science), requireNotNull(g.society), requireNotNull(g.technology), requireNotNull(g.history)) })
            a.academicRecord!!.subjectGrades.forEach { (semester, grades) ->
                hs.kr.entrydsm.application.application.service.validateSchoolGrades(semester, grades)
            }
        }
        require(a.graduationType != GraduationType.PROSPECTIVE ||
            a.academicRecord?.subjectGrades?.containsKey(SchoolSemester.THIRD_GRADE_SECOND_SEMESTER) != true)
        if (a.graduationType != GraduationType.GED && c.graduationType != null) require(a.middleSchoolInfo != null)
    }

    private fun changedFields(before: Applicant, after: Applicant): Set<String> {
        fun values(a: Applicant): Map<String, Any?> = linkedMapOf(
            "admissionType" to a.admissionType, "region" to a.region, "graduationType" to a.graduationType,
            "graduationDate" to a.graduationDate, "photoFileId" to a.photoFileId, "name" to a.name,
            "phoneNumber" to a.phoneNumber, "gender" to a.gender, "birthdate" to a.birthdate,
            "specialAdmissionType" to a.specialAdmissionType, "guardianName" to a.guardianName,
            "guardianPhoneNumber" to a.guardianPhoneNumber, "guardianGender" to a.guardianGender,
            "guardianRelation" to a.guardianRelation, "zipCode" to a.zipCode, "addressBase" to a.addressBase,
            "addressDetail" to a.addressDetail, "introduction" to a.introduction, "studyPlan" to a.studyPlan,
            "school" to a.middleSchoolInfo?.copy(schoolAddress = null), "academicRecord" to a.academicRecord,
        )
        val old = values(before)
        return values(after).filter { (key, value) -> old[key] != value }.keys
    }
}
