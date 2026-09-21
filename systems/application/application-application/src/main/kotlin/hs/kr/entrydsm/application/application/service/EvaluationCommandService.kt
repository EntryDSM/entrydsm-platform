package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.exception.AuthenticationRequiredException
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.nowUtc
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import java.time.LocalDateTime

class EvaluationCommandService(
    private val applicantRepository: ApplicantRepository,
    private val scoreCalculator: ScoreCalculator,
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
        val applicant = getApplicantByAccountId(accountId)
        require(applicant.graduationType != GraduationType.GED) {
            "subject grades are unavailable for GED applicants"
        }
        val record = getOrCreateAcademicRecord(applicant)
        record.gedScores = null
        record.subjectGrades[schoolSemester] = subjectGrades
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun saveGedScores(accountId: Long?, gedScores: GedScores) {
        val applicant = getApplicantByAccountId(accountId)
        require(applicant.graduationType == GraduationType.GED) {
            "GED scores are available only for GED applicants"
        }
        val record = getOrCreateAcademicRecord(applicant)
        record.subjectGrades.clear()
        record.gedScores = gedScores
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun saveAcademicRecord(
        accountId: Long?,
        absentCount: Int,
        earlyLeaveCount: Int,
        lateCount: Int,
        classAbsenceCount: Int,
        volunteerTime: Int,
    ): AcademicRecord {
        require(absentCount >= 0) { "absentCount must be greater than or equal to 0" }
        require(earlyLeaveCount >= 0) { "earlyLeaveCount must be greater than or equal to 0" }
        require(lateCount >= 0) { "lateCount must be greater than or equal to 0" }
        require(classAbsenceCount >= 0) { "classAbsenceCount must be greater than or equal to 0" }
        require(volunteerTime >= 0) { "volunteerTime must be greater than or equal to 0" }

        val applicant = getApplicantByAccountId(accountId)
        val record = getOrCreateAcademicRecord(applicant)
        record.absentCount = absentCount
        record.earlyLeaveCount = earlyLeaveCount
        record.lateCount = lateCount
        record.classAbsenceCount = classAbsenceCount
        record.volunteerTime = volunteerTime
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
        return record
    }

    fun saveCertificates(
        accountId: Long?,
        isDsmAlgorithmAwarded: Boolean,
        isProgrammingCertified: Boolean,
    ) {
        val applicant = getApplicantByAccountId(accountId)
        val record = getOrCreateAcademicRecord(applicant)
        record.isDsmAlgorithmAwarded = isDsmAlgorithmAwarded
        record.isProgrammingCertified = isProgrammingCertified
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun calculateResult(accountId: Long?) {
        val applicant = getApplicantByAccountId(accountId)
        applicant.totalScore = scoreCalculator.calculate(applicant)
        applicant.totalScoreUpdatedAt = nowUtc()
        applicant.touch()
        applicantRepository.save(applicant)
    }

    private fun getApplicantByAccountId(accountId: Long?): Applicant {
        val id = requireAccountId(accountId)
        return applicantRepository.findByAccountId(id)
            ?: throw ApplicantNotFoundException(id)
    }

    private fun requireAccountId(accountId: Long?): Long =
        accountId ?: throw AuthenticationRequiredException()

    private fun getOrCreateAcademicRecord(applicant: Applicant): AcademicRecord {
        return applicant.academicRecord ?: AcademicRecord()
    }
}
