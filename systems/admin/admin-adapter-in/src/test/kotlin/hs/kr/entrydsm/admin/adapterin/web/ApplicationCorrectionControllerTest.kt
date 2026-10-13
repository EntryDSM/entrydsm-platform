package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.admin.domain.port.`in`.ApplicationCorrectionResult
import hs.kr.entrydsm.admin.domain.port.`in`.CorrectApplicationUseCase
import org.junit.Assert.*
import org.junit.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ApplicationCorrectionControllerTest {
    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any? = { name, _ -> error("unexpected call: $name") }): T = type.cast(java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args ?: emptyArray()) })
    @Test fun individualApplicantChangesRequireAdministratorAndOnlyPassTargetId() {
        val calls = mutableListOf<Pair<String, Long>>()
        val screening = proxy(hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFirstScreeningUseCase::class.java) { name, args -> calls.add(name to (args[0] as Long)); Unit }
        val controller = ScreeningController(
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadScorePolicyUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.UpdateScorePolicyUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadAdmissionQuotaUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.UpdateAdmissionQuotaUseCase::class.java), screening,
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.EvaluateFinalScreeningUseCase::class.java),
            proxy(hs.kr.entrydsm.admin.domain.port.`in`.ReadStatisticsUseCase::class.java))
        val mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(GlobalExceptionHandler())
            .addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/screenings/first/applicants/5"
        for (request in listOf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path),
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path))) {
            assertEquals(401, mvc.perform(request).andReturn().response.status)
        }
        assertEquals(403, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("X-User-Id", "10").header("X-User-Role", "USER")).andReturn().response.status)
        assertTrue(calls.isEmpty())
        assertEquals(204, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
        assertEquals(204, mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path)
            .header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response.status)
        assertEquals(listOf("selectFirst" to 5L, "cancelFirst" to 5L), calls)
    }

    @Test fun editableFieldsRequiresAdministratorAndReturnsUseCaseFields() {
        val useCase = object : CorrectApplicationUseCase {
            override fun correct(command: hs.kr.entrydsm.admin.domain.command.CorrectApplicationCommand, editorId: String): ApplicationCorrectionResult =
                error("GET 요청이 원서를 수정하면 안 된다")
            override fun editableFields(applicantId: Long): hs.kr.entrydsm.admin.domain.port.`in`.EditableApplicationFields {
                assertEquals(5L, applicantId)
                return hs.kr.entrydsm.admin.domain.port.`in`.EditableApplicationFields(true, listOf("name", "admissionType"), null)
            }
        }
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(useCase))
            .setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/applicants/5/application"
        assertEquals(401, mvc.perform(get(path)).andReturn().response.status)
        assertEquals(403, mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "USER")).andReturn().response.status)
        val response = mvc.perform(get(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")).andReturn().response
        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"editable\":true"))
        assertTrue(response.contentAsString.contains("\"name\""))
        assertEquals(404, mvc.perform(get("$path/editable-fields").header("X-User-Id", "10")
            .header("X-User-Role", "ADMIN")).andReturn().response.status)
    }

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

    @Test fun correctionReasonValidationIsPreservedAfterRequestMove() {
        var calls = 0
        val mvc = MockMvcBuilders.standaloneSetup(ApplicationCorrectionController(CorrectApplicationUseCase { command, _ ->
            calls++
            ApplicationCorrectionResult(command.applicantId, 4L)
        })).setControllerAdvice(GlobalExceptionHandler()).addInterceptors(AdminAuthorizationInterceptor()).build()
        val path = "/api/v11/admin/applicants/5/application"
        for ((reason, expectedStatus) in listOf("" to 400, "   " to 400, "가".repeat(501) to 400, "가".repeat(500) to 200)) {
            val json = """{"reason":"$reason","changes":{"admissionType":"MEISTER"}}"""
            assertEquals(expectedStatus, mvc.perform(patch(path).header("X-User-Id", "10").header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response.status)
        }
        assertEquals(1, calls)
    }
}
