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
class AdmissionFileController(
    private val createExportUseCase: CreateExportUseCase,
) {
    @GetMapping(AdminEndpointPaths.ADMISSION_FILE)
    fun getAdmissionFile(): ResponseEntity<ApiResponse<FirstPassExportResponse>> =
        ResponseEntity.ok(
            ApiResponse(
                data = createExportUseCase.create(CreateExportCommand(ExportType.ADMISSION_FILE)).toFirstPassResponse(),
            ),
        )
}
