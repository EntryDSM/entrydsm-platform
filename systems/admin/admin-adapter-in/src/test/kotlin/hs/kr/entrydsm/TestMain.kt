package hs.kr.entrydsm.admin.adapterin

import hs.kr.entrydsm.admin.adapterin.web.SupportController
import hs.kr.entrydsm.admin.adapterin.web.ApplicantController
import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toDetailResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.request.CreateExportRequest
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.GenderRatio
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
import hs.kr.entrydsm.admin.domain.model.GedScores
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.`in`.AnswerQuestionUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.DeleteNoticeUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.DeleteApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.IssueExamineeNumberUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateApplicantUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.ReadExportUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.UpdateNoticeUseCase
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.http.HttpMethod
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.resource.NoResourceFoundException

class AdminAdapterInModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    /** 없앤 API(#195 에서 document 로 넘긴 개별 수험표)를 계속 부르는 클라이언트도 500 이 아니라 404 다. */
    @Test
    fun mapsUnknownPathToNotFoundResponse() {
        val response = GlobalExceptionHandler().handleApiNotFound(
            NoResourceFoundException(
                HttpMethod.GET,
                "/api/v11/admin/applicants/1/admission-ticket",
                "api/v11/admin/applicants/1/admission-ticket",
            ),
        )

        assertEquals(404, response.statusCode.value())
        assertEquals("API_NOT_FOUND", response.body?.error?.code)
    }

    @Test
    fun mapsUnsupportedMethodToMethodNotAllowedResponseWithAllowHeader() {
        val response = GlobalExceptionHandler()
            .handleMethodNotAllowed(HttpRequestMethodNotSupportedException("DELETE", listOf("GET")))

        assertEquals(405, response.statusCode.value())
        assertEquals("METHOD_NOT_ALLOWED", response.body?.error?.code)
        assertEquals(setOf(HttpMethod.GET), response.headers.allow)
    }

    @Test
    fun mapsGenderAndRegionStatisticsToResponse() {
        val response = ApplicantStatistics(
            generatedAt = Instant.EPOCH,
            genderRatio = GenderRatio(
                total = 2,
                byGender = mapOf(Gender.MALE to 1, Gender.FEMALE to 1),
                maleRatio = 0.5,
                byType = mapOf(AdmissionType.GENERAL to mapOf(Gender.MALE to 1)),
            ),
            regionDistribution = mapOf(ResidenceRegion.DAEJEON to 1, ResidenceRegion.CHUNGNAM to 1),
        ).toResponse()

        assertTrue(response.metrics.containsKey("GENDER_RATIO"))
        assertEquals(mapOf("DAEJEON" to 1L, "CHUNGNAM" to 1L), response.metrics["REGION_DISTRIBUTION"])
    }

    @Test
    fun mapsGedScoresToApplicantDetailResponse() {
        val response = ApplicantDetail(
            applicant = Applicant(id = 1L),
            photoFileId = null,
            introduction = null,
            studyPlan = null,
            score = null,
            gedScores = GedScores(95, 90, 85, 80, 75, 70, 65),
        ).toDetailResponse()

        assertEquals(listOf(95, 90, 85, 80, 75, 70, 65), response.gedScores?.let {
            listOf(it.korean, it.society, it.history, it.math, it.science, it.technology, it.english)
        })
    }

    @Test
    fun createsExportJobsForAllFileTypes() {
        val types = mutableListOf<ExportType>()
        val controller = SupportController(
            createExportUseCase = object : CreateExportUseCase {
                override fun create(command: CreateExportCommand): ExportJob {
                    types += command.type
                    return ExportJob(
                        exportJobId = "exp_test",
                        type = command.type,
                        status = ExportStatus.PENDING,
                        createdAt = Instant.EPOCH,
                    )
                }
            },
            readExportUseCase = unused(ReadExportUseCase::class.java),
            createNoticeUseCase = unused(CreateNoticeUseCase::class.java),
            updateNoticeUseCase = unused(UpdateNoticeUseCase::class.java),
            deleteNoticeUseCase = unused(DeleteNoticeUseCase::class.java),
            answerQuestionUseCase = unused(AnswerQuestionUseCase::class.java),
        )

        ExportType.entries.forEach { type ->
            val response = controller.createExport(CreateExportRequest(type))
            assertEquals(202, response.statusCode.value())
            assertEquals("exp_test", response.body?.data?.exportJobId)
        }
        assertEquals(ExportType.entries, types)
    }

    @Test
    fun deletesApplicantWithNoContent() {
        var deletedId: Long? = null
        val controller = ApplicantController(
            unused(ReadApplicantUseCase::class.java),
            unused(UpdateApplicantUseCase::class.java),
            unused(IssueExamineeNumberUseCase::class.java),
            DeleteApplicantUseCase { deletedId = it },
        )

        val response = controller.delete(7L)

        assertEquals(204, response.statusCode.value())
        assertEquals(7L, deletedId)
    }

    private fun <T> unused(type: Class<T>): T = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(type),
    ) { _, method, _ -> error("unexpected call: ${method.name}") } as T
}
