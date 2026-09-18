package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.exception.GlobalExceptionHandler
import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolResult
import hs.kr.entrydsm.application.application.port.`in`.result.MiddleSchoolSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MiddleSchoolControllerTest {
    @Test
    fun getMiddleSchoolsReturnsSchoolsAndTotalCount() {
        var command: SearchMiddleSchoolCommand? = null
        val mvc = MockMvcBuilders.standaloneSetup(
            MiddleSchoolController {
                command = it
                MiddleSchoolSearchResult(
                    schools = listOf(
                        MiddleSchoolResult(
                            code = "7441263",
                            name = "대전대성여자중학교",
                            address = "대전 주소",
                        ),
                    ),
                    totalCount = 1,
                )
            },
        ).setControllerAdvice(GlobalExceptionHandler()).build()

        val response = mvc.perform(
            get("/api/application/v11/middle-school")
                .param("name", " 대성 "),
        ).andReturn().response

        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertEquals(SearchMiddleSchoolCommand(name = "대성"), command)
        assertTrue(body, body.contains("\"success\":true"))
        assertTrue(body, body.contains("\"schools\":[{\"address\":\"대전 주소\",\"code\":\"7441263\",\"name\":\"대전대성여자중학교\"}]"))
        assertTrue(body, body.contains("\"totalCount\":1"))
    }

    @Test
    fun getMiddleSchoolsReturnsNullAddress() {
        val mvc = MockMvcBuilders.standaloneSetup(
            MiddleSchoolController {
                MiddleSchoolSearchResult(
                    schools = listOf(
                        MiddleSchoolResult(
                            code = "7441263",
                            name = "대전대성여자중학교",
                            address = null,
                        ),
                    ),
                    totalCount = 1,
                )
            },
        ).setControllerAdvice(GlobalExceptionHandler()).build()

        val response = mvc.perform(
            get("/api/application/v11/middle-school")
                .param("name", "대성"),
        ).andReturn().response

        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertTrue(body, body.contains("\"address\":null"))
        assertTrue(body, body.contains("\"totalCount\":1"))
    }

    @Test
    fun getMiddleSchoolsReturnsBadRequestWhenNameIsBlank() {
        var portCalled = false

        val mvc = MockMvcBuilders.standaloneSetup(
            MiddleSchoolController {
                portCalled = true
                MiddleSchoolSearchResult(
                    schools = emptyList(),
                    totalCount = 0,
                )
            },
        ).setControllerAdvice(GlobalExceptionHandler()).build()

        val response = mvc.perform(
            get("/api/application/v11/middle-school")
                .param("name", "   "),
        ).andReturn().response

        assertEquals(400, response.status)
        assertFalse(portCalled)
    }
}