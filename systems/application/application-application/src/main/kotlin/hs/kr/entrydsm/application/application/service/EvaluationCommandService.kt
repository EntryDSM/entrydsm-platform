package hs.kr.entrydsm.application.application.service

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
        saveSubjectGrades(command.applicantId, command.schoolSemester, command.subjectGrades)
    }

    override fun saveGedScores(command: SaveGedScoresCommand) {
        saveGedScores(command.applicantId, command.gedScores)
    }

    override fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult {
        val record = saveAcademicRecord(
            applicantId = command.applicantId,
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
            command.applicantId,
            command.isDsmAlgorithmAwarded,
            command.isProgrammingCertified,
        )
    }

    override fun calculateResult(command: CalculateEvaluationCommand): EvaluationResult {
        return EvaluationResult(calculateResult(command.applicantId))
    }

    fun saveSubjectGrades(
        applicantId: Long,
        schoolSemester: SchoolSemester,
        subjectGrades: SubjectGrades,
    ) {
        val applicant = getApplicant(applicantId)
        val record = getOrCreateAcademicRecord(applicant)
        record.subjectGrades[schoolSemester] = subjectGrades
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun saveGedScores(applicantId: Long, gedScores: GedScores) {
        validateScore(gedScores.koreanScore)
        validateScore(gedScores.mathScore)
        validateScore(gedScores.englishScore)
        validateScore(gedScores.scienceScore)
        validateScore(gedScores.societyScore)
        validateScore(gedScores.technologyScore)
        validateScore(gedScores.historyScore)

        val applicant = getApplicant(applicantId)
        val record = getOrCreateAcademicRecord(applicant)
        record.gedScores = gedScores
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun saveAcademicRecord(
        applicantId: Long,
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

        val applicant = getApplicant(applicantId)
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
        isDsmAlgorithmAwarded: Boolean,
        isProgrammingCertified: Boolean,
    ) {
        val applicant = getApplicant(applicantId)
        val record = getOrCreateAcademicRecord(applicant)
        record.isDsmAlgorithmAwarded = isDsmAlgorithmAwarded
        record.isProgrammingCertified = isProgrammingCertified
        applicant.academicRecord = record
        applicant.touch()
        applicantRepository.save(applicant)
    }

    fun calculateResult(applicantId: Long): Map<String, Double> {
        val applicant = getApplicant(applicantId)
        val result = scoreCalculator.calculate(applicant)
        applicant.totalScore = result["REGULAR"]
        applicant.totalScoreUpdatedAt = LocalDateTime.now()
        applicantRepository.save(applicant)
        return result
    }

    private fun getApplicant(applicantId: Long): Applicant {
        return applicantRepository.findById(applicantId)
            ?: throw ApplicantNotFoundException(applicantId)
    }

    private fun getOrCreateAcademicRecord(applicant: Applicant): AcademicRecord {
        return applicant.academicRecord ?: AcademicRecord()
    }

    private fun validateScore(score: Int) {
        require(score in 0..100) { "score must be between 0 and 100" }
    }
}
