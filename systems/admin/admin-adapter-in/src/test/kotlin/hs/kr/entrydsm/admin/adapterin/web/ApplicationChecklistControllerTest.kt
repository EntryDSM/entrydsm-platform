package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationChecklistControllerTest {
    @Test
    fun getApplicationChecklistCreatesChecklistExport() {
        assertEquals("/api/v11/admin/application-checklist", AdminEndpointPaths.APPLICATION_CHECKLIST)
        var type: ExportType? = null
        val controller = ApplicationChecklistController(
            object : CreateExportUseCase {
                override fun create(command: CreateExportCommand): ExportJob {
                    type = command.type
                    return ExportJob(
                        exportJobId = "exp_test",
                        type = command.type,
                        status = ExportStatus.PENDING,
                        createdAt = Instant.EPOCH,
                    )
                }
            },
        )

        val response = controller.getApplicationChecklist()

        assertEquals(ExportType.APPLICATION_CHECKLIST, type)
        assertEquals("exp_test", response.body?.data?.jobId)
    }
}
