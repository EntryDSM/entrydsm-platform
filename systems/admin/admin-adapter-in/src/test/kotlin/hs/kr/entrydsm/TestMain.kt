package hs.kr.entrydsm.admin.adapterin

import hs.kr.entrydsm.admin.adapterin.web.SupportController
import hs.kr.entrydsm.admin.adapterin.web.ApplicantController
import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.GenderRatio
import hs.kr.entrydsm.admin.domain.model.RegionStatus
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.`in`.AnswerQuestionUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import hs.kr.entrydsm.admin.domain.port.`in`.CreateFirstPassFileUseCase
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

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
            regionStatus = RegionStatus(
                total = 2,
                byScope = mapOf("LOCAL" to 1, "NATIONWIDE" to 1),
                byRegion = mapOf(ResidenceRegion.DAEJEON to 1, ResidenceRegion.CHUNGNAM to 1),
            ),
        ).toResponse()

        assertTrue(response.metrics.containsKey("GENDER_RATIO"))
        assertTrue(response.metrics.containsKey("REGION_STATUS"))
    }

    @Test
    fun createsFirstPassExportJob() {
        val controller = SupportController(
            createExportUseCase = object : CreateExportUseCase {
                override fun create(command: CreateExportCommand): ExportJob {
                    return ExportJob(
                        exportJobId = "exp_test",
                        type = command.type,
                        status = ExportStatus.PENDING,
                        createdAt = Instant.EPOCH,
                    )
                }
            },
            createFirstPassFileUseCase = CreateFirstPassFileUseCase {
                hs.kr.entrydsm.admin.domain.model.DownloadLink("https://example.test/first-pass", Instant.EPOCH)
            },
            readExportUseCase = unused(ReadExportUseCase::class.java),
            createNoticeUseCase = unused(CreateNoticeUseCase::class.java),
            updateNoticeUseCase = unused(UpdateNoticeUseCase::class.java),
            deleteNoticeUseCase = unused(DeleteNoticeUseCase::class.java),
            answerQuestionUseCase = unused(AnswerQuestionUseCase::class.java),
        )

        val response = MockMvcBuilders.standaloneSetup(controller).build()
            .perform(get("/api/v11/admin/first-pass"))
            .andReturn()
            .response
        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertTrue(body, body.contains("\"downloadUrl\":\"https://example.test/first-pass\""))
        assertTrue(body, body.contains("\"expiresAt\":\"1970-01-01T00:00:00Z\""))
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
