package hs.kr.entrydsm.admin.adapterin.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdmissionFileControllerTest {
    @Test
    fun getAdmissionFileReturnsEmptyDownloadInformation() {
        val response = MockMvcBuilders.standaloneSetup(AdmissionFileController())
            .build()
            .perform(get(AdminEndpointPaths.ADMISSION_FILE))
            .andReturn()
            .response
        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertTrue(body, body.contains("\"downloadUrl\":null"))
        assertTrue(body, body.contains("\"expiresAt\":null"))
    }
}
