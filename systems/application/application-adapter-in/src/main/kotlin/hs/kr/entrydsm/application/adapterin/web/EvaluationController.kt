package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.application.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveAcademicRecordRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveCertificatesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveGedScoresRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveSubjectGradesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.response.AcademicRecordResponse
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.model.GedScores
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/evaluation/v11/evaluations")
class EvaluationController(
    private val evaluationPort: EvaluationPort,
) {
    @PostMapping("/grades/expected")
    fun saveExpectedGrades(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestBody request: SaveSubjectGradesRequest,
    ): ApiResponse<Unit> {
        saveSubjectGrades(accountId, request)
        return ApiResponse(data = null)
    }

    @PostMapping("/grades/graduated")
    fun saveGraduatedGrades(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestBody request: SaveSubjectGradesRequest,
    ): ApiResponse<Unit> {
        saveSubjectGrades(accountId, request)
        return ApiResponse(data = null)
    }

    @PostMapping("/ged-scores")
    fun saveGedScores(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestBody request: SaveGedScoresRequest,
    ): ApiResponse<Unit> {
        evaluationPort.saveGedScores(
            SaveGedScoresCommand(
                accountId = accountId,
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
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestBody request: SaveAcademicRecordRequest,
    ): ApiResponse<AcademicRecordResponse> {
        val result = evaluationPort.saveAcademicRecord(
            SaveAcademicRecordCommand(
                accountId = accountId,
                absentCount = request.absentCount,
                earlyLeaveCount = request.earlyLeaveCount,
                lateCount = request.lateCount,
                classAbsenceCount = request.classAbsenceCount,
                volunteerTime = request.volunteerTime,
            ),
        )
        return ApiResponse(data = result.toResponse())
    }

    @PostMapping("/certificates")
    fun saveCertificates(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
        @RequestBody request: SaveCertificatesRequest,
    ): ApiResponse<Unit> {
        evaluationPort.saveCertificates(
            SaveCertificatesCommand(
                accountId = accountId,
                isDsmAlgorithmAwarded = request.isDsmAlgorithmAwarded,
                isProgrammingCertified = request.isProgrammingCertified,
            ),
        )
        return ApiResponse(data = null)
    }

    @PostMapping("/result")
    fun getResult(
        @RequestHeader(USER_ID_HEADER) accountId: Long,
    ): ApiResponse<Unit> {
        evaluationPort.calculateResult(
            CalculateEvaluationCommand(
                accountId = accountId,
            ),
        )
        return ApiResponse(data = null)
    }

    private fun saveSubjectGrades(
        accountId: Long?,
        request: SaveSubjectGradesRequest,
    ) {
        evaluationPort.saveSubjectGrades(
            SaveSubjectGradesCommand(
                accountId = accountId,
                schoolSemester = request.schoolSemester.toSchoolSemester(),
                subjectGrades = request.subjects.toDomain(),
            ),
        )
    }

    private companion object {
        const val USER_ID_HEADER = "X-USER-ID"
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
