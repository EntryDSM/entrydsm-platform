package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.application.application.port.`in`.MiddleSchoolPort
import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolResult
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MiddleSchoolControllerTest {
    @Test
    fun getMiddleSchoolsReturnsSchoolsAndHasNext() {
        var command: SearchMiddleSchoolCommand? = null
        val mvc = mockMvc {
            command = it
            MiddleSchoolSearchResult(listOf(MiddleSchoolResult(code = "7441263", name = "대전대성여자중학교")), hasNext = true)
        }

        val response = mvc.perform(
            get("/api/application/v11/middle-school").param("name", " 대성 ").param("page", "1").param("size", "5"),
        ).andReturn().response
        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertEquals(SearchMiddleSchoolCommand(name = "대성", page = 1, size = 5), command)
        assertTrue(body, body.contains("\"success\":true"))
        assertTrue(body, body.contains("\"schools\":[{\"code\":\"7441263\",\"name\":\"대전대성여자중학교\"}]"))
        assertTrue(body, body.contains("\"hasNext\":true"))
    }

    @Test
    fun invalidPagingReturns400() {
        val mvc = mockMvc { error("port must not be called") }

        for (query in listOf("page=-1", "size=0", "size=101", "page=2147483647", "page=a")) {
            val response = mvc.perform(get("/api/application/v11/middle-school?$query")).andReturn().response

            assertEquals(query, 400, response.status)
        }
    }

    private fun mockMvc(port: MiddleSchoolPort): MockMvc =
        MockMvcBuilders.standaloneSetup(MiddleSchoolController(port))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
}
