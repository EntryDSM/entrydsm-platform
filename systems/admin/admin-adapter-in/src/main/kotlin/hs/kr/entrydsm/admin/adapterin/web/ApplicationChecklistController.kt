package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.common.toFirstPassResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.FirstPassExportResponse
import hs.kr.entrydsm.admin.domain.command.CreateExportCommand
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.port.`in`.CreateExportUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ApplicationChecklistController(
    private val createExportUseCase: CreateExportUseCase,
) {
    @GetMapping(AdminEndpointPaths.APPLICATION_CHECKLIST)
    fun getApplicationChecklist(): ResponseEntity<ApiResponse<FirstPassExportResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = createExportUseCase.create(CreateExportCommand(ExportType.APPLICATION_CHECKLIST))
                    .toFirstPassResponse(),
            ),
        )
}
