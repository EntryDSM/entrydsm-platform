package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.domain.port.`in`.ApplicationCorrectionResult
import hs.kr.entrydsm.admin.domain.port.`in`.CorrectApplicationUseCase
import org.junit.Assert.*
import org.junit.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ApplicationCorrectionControllerTest {
    @Test fun permissionIsRequiredAndRequestDoesNotNeedVersion() {
        var calls = 0
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(CorrectApplicationUseCase { command, _ ->
            calls++
            assertEquals(5L, command.applicantId)
            assertEquals("MEISTER", command.changes.admissionType)
            ApplicationCorrectionResult(command.applicantId, 4L)
        })).setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val json = """{"reason":"입력 오류 정정","changes":{"admissionType":"MEISTER"}}"""
        val path = "/api/v11/admin/applicants/5/application"
        assertEquals(401, mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        assertEquals(403, mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "USER")
            .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        assertEquals(0, calls)
        val result = mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")
            .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn()
        val response = result.response
        assertEquals(result.resolvedException?.toString(), 200, response.status)
        assertTrue(response.contentAsString.contains("\"version\":4"))
        assertEquals(1, calls)
    }
}
