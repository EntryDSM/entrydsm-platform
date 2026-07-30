package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.application.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveAcademicRecordRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveCertificatesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveGedScoresRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveSubjectGradesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.response.AcademicRecordResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.EvaluationResultResponse
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.GedScores
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/evaluation/v11/evaluations")
class EvaluationController(
    private val evaluationPort: EvaluationPort,
) {
    @PostMapping("/grades/expected")
    fun saveExpectedGrades(
        @RequestBody request: SaveSubjectGradesRequest,
    ): ApiResponse<Unit> {
        saveSubjectGrades(request)
        return ApiResponse(data = null)
    }

    @PostMapping("/grades/graduated")
    fun saveGraduatedGrades(
        @RequestBody request: SaveSubjectGradesRequest,
    ): ApiResponse<Unit> {
        saveSubjectGrades(request)
        return ApiResponse(data = null)
    }

    @PostMapping("/ged-scores")
    fun saveGedScores(
        @RequestBody request: SaveGedScoresRequest,
    ): ApiResponse<Unit> {
        evaluationPort.saveGedScores(
            SaveGedScoresCommand(
                applicantId = request.applicantId,
                gedScores = GedScores(
                    koreanScore = request.koreanScore,
                    mathScore = request.mathScore,
                    englishScore = request.englishScore,
                    scienceScore = request.scienceScore,
                    societyScore = request.societyScore,
                    technologyScore = request.technologyScore,
                    historyScore = request.historyScore,
                ),
            ),
        )
        return ApiResponse(data = null)
    }

    @PostMapping("/academic-records")
    fun saveAcademicRecords(
        @RequestBody request: SaveAcademicRecordRequest,
    ): ApiResponse<AcademicRecordResponse> {
        val result = evaluationPort.saveAcademicRecord(
            SaveAcademicRecordCommand(
                applicantId = request.applicantId,
                absentCount = request.absentCount,
                earlyLeaveCount = request.earlyLeaveCount,
                lateCount = request.lateCount,
                classAbsenceCount = request.classAbsenceCount,
                volunteerTime = request.resolvedVolunteerTime(),
            ),
        )
        return ApiResponse(data = result.toResponse())
    }

    @PostMapping("/certificates")
    fun saveCertificates(
        @RequestBody request: SaveCertificatesRequest,
    ): ApiResponse<Unit> {
        evaluationPort.saveCertificates(
            SaveCertificatesCommand(
                applicantId = request.applicantId,
                isDsmAlgorithmAwarded = request.isDsmAlgorithmAwarded,
                isProgrammingCertified = request.isProgrammingCertified,
            ),
        )
        return ApiResponse(data = null)
    }

    @GetMapping("/result")
    fun getResult(
        @RequestParam applicantId: Long,
    ): ApiResponse<EvaluationResultResponse> {
        val result = evaluationPort.calculateResult(CalculateEvaluationCommand(applicantId))
        return ApiResponse(data = result.toResponse())
    }

    private fun saveSubjectGrades(request: SaveSubjectGradesRequest) {
        evaluationPort.saveSubjectGrades(
            SaveSubjectGradesCommand(
                applicantId = request.applicantId,
                schoolSemester = request.schoolSemester.toSchoolSemester(),
                subjectGrades = request.subjects.toDomain(),
            ),
        )
    }
}

private fun String.toSchoolSemester(): SchoolSemester {
    return when (this) {
        "2-1" -> SchoolSemester.SECOND_GRADE_FIRST_SEMESTER
        "2-2" -> SchoolSemester.SECOND_GRADE_SECOND_SEMESTER
        "3-1" -> SchoolSemester.THIRD_GRADE_FIRST_SEMESTER
        "3-2" -> SchoolSemester.THIRD_GRADE_SECOND_SEMESTER
        else -> throw IllegalArgumentException("schoolSemester must be one of 2-1, 2-2, 3-1, 3-2")
    }
}
