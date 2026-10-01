package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.dto.request.SaveSubjectGradesRequest
import hs.kr.entrydsm.application.adapterin.web.dto.request.SubjectGradesRequest
import hs.kr.entrydsm.application.application.port.`in`.EvaluationPort
import hs.kr.entrydsm.application.application.port.`in`.command.CalculateEvaluationCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveAcademicRecordCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveCertificatesCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveGedScoresCommand
import hs.kr.entrydsm.application.application.port.`in`.command.SaveSubjectGradesCommand
import hs.kr.entrydsm.application.application.port.`in`.result.AcademicRecordResult
import hs.kr.entrydsm.application.domain.enum.SchoolSemester
import hs.kr.entrydsm.application.domain.enum.SubjectGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import hs.kr.entrydsm.application.adapterin.web.exception.GlobalExceptionHandler
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class EvaluationControllerTest {
    @Test
    fun gedScoresIdentifyEachSubjectAndAcceptBoundaryValues() {
        val controller = EvaluationController(FakeEvaluationPort())
        val scores = hs.kr.entrydsm.application.adapterin.web.dto.request.SaveGedScoresRequest(0, 0, 0, 0, 0, 0, 0)
        val cases = listOf(
            "KOREAN" to scores.copy(koreanScore = -1),
            "MATH" to scores.copy(mathScore = 101),
            "ENGLISH" to scores.copy(englishScore = -1),
            "SCIENCE" to scores.copy(scienceScore = 101),
            "SOCIETY" to scores.copy(societyScore = -1),
            "TECHNOLOGY" to scores.copy(technologyScore = 101),
            "HISTORY" to scores.copy(historyScore = -1),
        )
        for ((subject, request) in cases) {
            val error = org.junit.Assert.assertThrows(hs.kr.entrydsm.application.application.exception.ApplicationValidationException::class.java) {
                controller.saveGedScores(10L, request)
            }
            assertEquals("APPLICATION_${subject}_SCORE_OUT_OF_RANGE", error.errorCode.name)
        }
        controller.saveGedScores(10L, scores)
        controller.saveGedScores(10L, hs.kr.entrydsm.application.adapterin.web.dto.request.SaveGedScoresRequest(100, 100, 100, 100, 100, 100, 100))
    }

    @Test
    fun resultReturnsDetailedBadRequestReason() {
        val port = FakeEvaluationPort()
        port.calculationFailure = hs.kr.entrydsm.application.application.exception.EvaluationValidationException("검정고시 성적이 누락되었습니다")
        val mvc = MockMvcBuilders.standaloneSetup(EvaluationController(port))
            .setControllerAdvice(GlobalExceptionHandler()).build()
        val response = mvc.perform(post("/api/evaluation/v11/evaluations/result")
            .header("X-USER-ID", 10L)).andReturn().response
        assertEquals(400, response.status)
        assertTrue(response.getContentAsString(Charsets.UTF_8).contains("검정고시 성적이 누락되었습니다"))
    }

    @Test
    fun academicRecordsRejectsNonPositiveValuesAndAcceptsPositiveValues() {
        val mvc = MockMvcBuilders.standaloneSetup(EvaluationController(FakeEvaluationPort()))
            .setControllerAdvice(GlobalExceptionHandler()).build()
        val fields = listOf("absentCount", "earlyLeaveCount", "lateCount", "classAbsenceCount", "volunteerTime")
        fields.forEach { field ->
            listOf(-1, 0).forEach { value ->
                val body = fields.joinToString(",", "{", "}") { "\"$it\":${if (it == field) value else 1}" }
                val response = mvc.perform(post("/api/evaluation/v11/evaluations/academic-records")
                    .header("X-USER-ID", 10L).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().response
                assertEquals(400, response.status)
            }
        }
        val response = mvc.perform(post("/api/evaluation/v11/evaluations/academic-records")
            .header("X-USER-ID", 10L).contentType(MediaType.APPLICATION_JSON)
            .content(fields.joinToString(",", "{", "}") { "\"$it\":1" })).andReturn().response
        assertEquals(200, response.status)
    }

    @Test
    fun subjectGradesRequestConvertsEverySubjectGrade() {
        val request = SubjectGradesRequest(
            koreanGrade = SubjectGrade.A,
            societyGrade = SubjectGrade.B,
            englishGrade = SubjectGrade.C,
            historyGrade = SubjectGrade.D,
            mathGrade = SubjectGrade.E,
            scienceGrade = SubjectGrade.X,
            technologyGrade = SubjectGrade.A,
        )

        val result = request.toDomain()

        assertEquals(SubjectGrade.A, result.koreanGrade)
        assertEquals(SubjectGrade.B, result.societyGrade)
        assertEquals(SubjectGrade.C, result.englishGrade)
        assertEquals(SubjectGrade.D, result.historyGrade)
        assertEquals(SubjectGrade.E, result.mathGrade)
        assertEquals(SubjectGrade.X, result.scienceGrade)
        assertEquals(SubjectGrade.A, result.technologyGrade)
    }

    @Test
    fun saveExpectedGradesConvertsSchoolSemester() {
        val evaluationPort = FakeEvaluationPort()
        val controller = EvaluationController(evaluationPort)

        controller.saveExpectedGrades(
            accountId = 10L,
            request = SaveSubjectGradesRequest(
                schoolSemester = "3-1",
                subjects = subjectGradesRequest(),
            ),
        )

        assertEquals(
            SchoolSemester.THIRD_GRADE_FIRST_SEMESTER,
            evaluationPort.saveSubjectGradesCommand?.schoolSemester,
        )
        assertEquals(10L, evaluationPort.saveSubjectGradesCommand?.accountId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveExpectedGradesRejectsInvalidSchoolSemester() {
        EvaluationController(FakeEvaluationPort()).saveExpectedGrades(
            accountId = 10L,
            request = SaveSubjectGradesRequest(
                schoolSemester = "1-1",
                subjects = subjectGradesRequest(),
            ),
        )
    }

    @Test
    fun getResultCalculatesAndDoesNotExposeScores() {
        val evaluationPort = FakeEvaluationPort()
        val controller = EvaluationController(evaluationPort)

        val response = controller.getResult(
            accountId = 10L,
        )

        assertEquals(null, response.data)
        assertEquals(10L, evaluationPort.calculateEvaluationCommand?.accountId)
    }

    private class FakeEvaluationPort : EvaluationPort {
        var calculationFailure: IllegalArgumentException? = null
        var saveSubjectGradesCommand: SaveSubjectGradesCommand? = null
        var calculateEvaluationCommand: CalculateEvaluationCommand? = null

        override fun saveSubjectGrades(command: SaveSubjectGradesCommand) {
            saveSubjectGradesCommand = command
        }

        override fun saveGedScores(command: SaveGedScoresCommand) = Unit

        override fun saveAcademicRecord(command: SaveAcademicRecordCommand): AcademicRecordResult =
            AcademicRecordResult(
                absentCount = 0,
                earlyLeaveCount = 0,
                lateCount = 0,
                classAbsenceCount = 0,
                volunteerTime = 0,
            )

        override fun saveCertificates(command: SaveCertificatesCommand) = Unit

        override fun calculateResult(command: CalculateEvaluationCommand) {
            calculationFailure?.let { throw it }
            calculateEvaluationCommand = command
        }
    }

    private companion object {
        fun subjectGradesRequest(): SubjectGradesRequest =
            SubjectGradesRequest(
                koreanGrade = SubjectGrade.A,
                societyGrade = SubjectGrade.A,
                englishGrade = SubjectGrade.A,
                historyGrade = SubjectGrade.A,
                mathGrade = SubjectGrade.A,
                scienceGrade = SubjectGrade.A,
                technologyGrade = SubjectGrade.A,
            )
    }
}
