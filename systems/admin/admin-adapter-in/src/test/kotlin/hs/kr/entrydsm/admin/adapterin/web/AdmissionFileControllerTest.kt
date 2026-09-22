package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AdmissionFileControllerTest {
    @Test
    fun getAdmissionFileCreatesAdmissionFileExport() {
        var type: ExportType? = null
        val controller = AdmissionFileController(object : CreateExportUseCase {
            override fun create(command: CreateExportCommand) = ExportJob(
                exportJobId = "exp_test",
                type = command.type.also { type = it },
                status = ExportStatus.PENDING,
                createdAt = Instant.EPOCH,
            )
        })
        val response = MockMvcBuilders.standaloneSetup(controller)
            .build()
            .perform(get(AdminEndpointPaths.ADMISSION_FILE))
            .andReturn()
            .response
        val body = response.getContentAsString(Charsets.UTF_8)

        assertEquals(200, response.status)
        assertEquals(ExportType.ADMISSION_FILE, type)
        assertTrue(body, body.contains("\"jobId\":\"exp_test\""))
        assertTrue(body, body.contains("\"downloadUrl\":null"))
    }
}
