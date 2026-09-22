package hs.kr.entrydsm.admin.adapterin

import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Gender
import hs.kr.entrydsm.admin.domain.enum.ResidenceRegion
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.GenderRatio
import hs.kr.entrydsm.admin.domain.model.RegionStatus
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
            regionStatus = RegionStatus(
                total = 2,
                byScope = mapOf("LOCAL" to 1, "NATIONWIDE" to 1),
                byRegion = mapOf(ResidenceRegion.DAEJEON to 1, ResidenceRegion.CHUNGNAM to 1),
            ),
        ).toResponse()

        assertTrue(response.metrics.containsKey("GENDER_RATIO"))
        assertTrue(response.metrics.containsKey("REGION_STATUS"))
    }
}
