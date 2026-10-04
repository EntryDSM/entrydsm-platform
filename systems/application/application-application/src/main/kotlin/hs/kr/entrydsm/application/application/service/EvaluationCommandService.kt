package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicationErrorCode.*
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import hs.kr.entrydsm.application.application.exception.EvaluationValidationException
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.ApplicationPeriodReader
import hs.kr.entrydsm.application.domain.enum.ApplicantStatus
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import org.springframework.transaction.annotation.Transactional

@Transactional
class EvaluationCommandService(
    private val applicantRepository: ApplicantRepository,
    private val scoreCalculator: ScoreCalculator,
    private val applicationPeriod: ApplicationPeriodReader,
) : EvaluationPort {
    override fun saveSubjectGrades(command: SaveSubjectGradesCommand) {
        saveSubjectGrades(command.accountId, command.schoolSemester, command.subjectGrades)
    }

    override fun saveGedScores(command: SaveGedScoresCommand) {
        saveGedScores(command.accountId, command.gedScores)
    }

    override fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult {
        val record = saveAcademicRecord(
            accountId = command.accountId,
            absentCount = command.absentCount,
            earlyLeaveCount = command.earlyLeaveCount,
            lateCount = command.lateCount,
            classAbsenceCount = command.classAbsenceCount,
            volunteerTime = command.volunteerTime,
        )
        return AcademicRecordResult(
            absentCount = record.absentCount,
            earlyLeaveCount = record.earlyLeaveCount,
            lateCount = record.lateCount,
            classAbsenceCount = record.classAbsenceCount,
            volunteerTime = record.volunteerTime,
        )
    }

    override fun saveCertificates(command: SaveCertificatesCommand) {
        saveCertificates(
            accountId = command.accountId,
            isDsmAlgorithmAwarded = command.isDsmAlgorithmAwarded,
            isProgrammingCertified = command.isProgrammingCertified,
        )
    }

    override fun calculateResult(command: CalculateEvaluationCommand) {
        calculateResult(command.accountId)
    }

    fun saveSubjectGrades(
        accountId: Long?,
        schoolSemester: SchoolSemester,
        subjectGrades: SubjectGrades,
    ) {
        val applicant = getWritableApplicant(accountId)
        APPLICATION_SUBJECTS_NOT_ALLOWED.requireValid(applicant.graduationType != GraduationType.GED)
        if (
            schoolSemester == SchoolSemester.THIRD_GRADE_FIRST_SEMESTER &&
                listOf(
                    subjectGrades.koreanGrade, subjectGrades.mathGrade, subjectGrades.englishGrade,
                    subjectGrades.scienceGrade, subjectGrades.societyGrade, subjectGrades.technologyGrade,
                    subjectGrades.historyGrade,
                ).all { it == SubjectGrade.X }
        ) { throw EvaluationValidationException("3학년 1학기 성적 입력은 필수입니다") }
        val record = getOrCreateAcademicRecord(applicant)
        record.gedScores = null
        record.subjectGrades[schoolSemester] = subjectGrades
        applicant.academicRecord = record
        saveEvaluation(applicant)
    }

    fun saveGedScores(accountId: Long?, gedScores: GedScores) {
        val applicant = getWritableApplicant(accountId)
        APPLICATION_GED_SCORES_NOT_ALLOWED.requireValid(applicant.graduationType == GraduationType.GED)
        val record = getOrCreateAcademicRecord(applicant)
        record.subjectGrades.clear()
        record.gedScores = gedScores
        applicant.academicRecord = record
        saveEvaluation(applicant)
    }

    fun saveAcademicRecord(
        accountId: Long?,
        absentCount: Int,
        earlyLeaveCount: Int,
        lateCount: Int,
        classAbsenceCount: Int,
        volunteerTime: Int,
    ): AcademicRecord {
        APPLICATION_ABSENT_COUNT_OUT_OF_RANGE.requireValid(absentCount >= 0)
        APPLICATION_EARLY_LEAVE_COUNT_OUT_OF_RANGE.requireValid(earlyLeaveCount >= 0)
        APPLICATION_LATE_COUNT_OUT_OF_RANGE.requireValid(lateCount >= 0)
        APPLICATION_CLASS_ABSENCE_COUNT_OUT_OF_RANGE.requireValid(classAbsenceCount >= 0)
        APPLICATION_VOLUNTEER_TIME_OUT_OF_RANGE.requireValid(volunteerTime >= 0)

        val applicant = getWritableApplicant(accountId)
        val record = getOrCreateAcademicRecord(applicant)
        record.absentCount = absentCount
        record.earlyLeaveCount = earlyLeaveCount
        record.lateCount = lateCount
        record.classAbsenceCount = classAbsenceCount
        record.volunteerTime = volunteerTime
        applicant.academicRecord = record
        saveEvaluation(applicant)
        return record
    }

    fun saveCertificates(
        accountId: Long?,
        isDsmAlgorithmAwarded: Boolean,
        isProgrammingCertified: Boolean,
    ) {
        val applicant = getWritableApplicant(accountId)
        val record = getOrCreateAcademicRecord(applicant)
        record.isDsmAlgorithmAwarded = isDsmAlgorithmAwarded
        record.isProgrammingCertified = isProgrammingCertified
        applicant.academicRecord = record
        saveEvaluation(applicant)
    }

    fun calculateResult(accountId: Long?) {
        val applicant = getWritableApplicant(accountId)
        applicant.totalScore = try {
            requireNotNull(applicant.graduationType) { "졸업 구분이 누락되었습니다" }
            requireNotNull(applicant.academicRecord) { "성적 및 출결·봉사활동 기록이 누락되었습니다" }
            scoreCalculator.calculate(applicant)
        } catch (exception: IllegalArgumentException) {
            throw EvaluationValidationException(exception.message ?: "평가에 필요한 성적을 확인해주세요")
        }
        applicant.totalScoreUpdatedAt = nowUtc()
        applicant.touch()
        applicantRepository.save(applicant)
    }

    private fun saveEvaluation(applicant: Applicant) {
        applicant.totalScore = null
        applicant.totalScoreUpdatedAt = null
        applicant.touch()
        applicantRepository.save(applicant)
    }

    /** 성적도 원서의 일부라 접수 기간에 작성 중인 원서만 수정한다. */
    private fun getWritableApplicant(accountId: Long?): Applicant {
        applicationPeriod.requireOpen()
        val id = requireAccountId(accountId)
        val applicant = applicantRepository.findByAccountId(id)
            ?: throw ApplicantNotFoundException(id)
        APPLICATION_NOT_EDITABLE.requireValid(applicant.status == ApplicantStatus.DRAFT)
        return applicant
    }

    private fun requireAccountId(accountId: Long?): Long =
        accountId ?: throw AuthenticationRequiredException()

    private fun getOrCreateAcademicRecord(applicant: Applicant): AcademicRecord {
        return applicant.academicRecord ?: AcademicRecord()
    }
}
