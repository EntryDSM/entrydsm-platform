package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantAccessDeniedException
import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.application.port.`in`.result.EvaluationResult
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.domain.enum.GraduationType
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.AcademicRecord
import hs.kr.entrydsm.application.domain.model.Applicant
import hs.kr.entrydsm.application.domain.model.GedScores
import hs.kr.entrydsm.application.domain.model.SubjectGrades
import hs.kr.entrydsm.application.domain.service.ScoreCalculator
import java.time.LocalDateTime
import org.springframework.stereotype.Service

@Service
class EvaluationCommandService(
    private val applicantRepository: ApplicantRepository,
) : EvaluationPort {
    private val scoreCalculator = ScoreCalculator()

    override fun saveSubjectGrades(command: SaveSubjectGradesCommand) {
        saveSubjectGrades(command.applicantId, command.userId, command.schoolSemester, command.subjectGrades)
    }

    override fun saveGedScores(command: SaveGedScoresCommand) {
        saveGedScores(command.applicantId, command.userId, command.gedScores)
    }

    override fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult {
        val record = saveAcademicRecord(
            applicantId = command.applicantId,
            userId = command.userId,
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
            applicantId = command.applicantId,
            userId = command.userId,
            isDsmAlgorithmAwarded = command.isDsmAlgorithmAwarded,
            isProgrammingCertified = command.isProgrammingCertified,
        )
    }

    override fun calculateResult(command: CalculateEvaluationCommand): EvaluationResult {
        return EvaluationResult(calculateResult(command.applicantId, command.userId))
    }

    fun saveSubjectGrades(
        applicantId: Long,
        userId: Long? = null,
        schoolSemester: SchoolSemester,
        subjectGrades: SubjectGrades,
    ) {
        val applicant = getApplicant(applicantId, userId)
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

    fun saveGedScores(applicantId: Long, userId: Long? = null, gedScores: GedScores) {
        val applicant = getApplicant(applicantId, userId)
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
        applicantId: Long,
        userId: Long? = null,
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

        val applicant = getApplicant(applicantId, userId)
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
        applicantId: Long,
        userId: Long? = null,
        isDsmAlgorithmAwarded: Boolean,
        isProgrammingCertified: Boolean,
    ) {
        val applicant = getApplicant(applicantId, userId)
        val record = getOrCreateAcademicRecord(applicant)
        record.isDsmAlgorithmAwarded = isDsmAlgorithmAwarded
        record.isProgrammingCertified = isProgrammingCertified
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun calculateResult(applicantId: Long, userId: Long? = null): Map<String, Double> {
        val applicant = getApplicant(applicantId, userId)
        val admissionType = requireNotNull(applicant.admissionType) {
            "admissionType is required"
        }
        val result = scoreCalculator.calculate(applicant)
        applicant.totalScore = result.getValue(admissionType)
        applicant.totalScoreUpdatedAt = LocalDateTime.now()
        applicant.touch()
        applicantRepository.save(applicant)
        return result.mapKeys { it.key.name }
    }

    private fun getApplicant(applicantId: Long, userId: Long? = null): Applicant {
        val applicant = applicantRepository.findById(applicantId)
            ?: throw ApplicantNotFoundException(applicantId)
        if (userId != null && applicant.accountId != userId) {
            throw ApplicantAccessDeniedException(applicantId)
        }
        return applicant
    }

    private fun getOrCreateAcademicRecord(applicant: Applicant): AcademicRecord {
        return applicant.academicRecord ?: AcademicRecord()
    }
}
