package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.AdmissionFileResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AdmissionFileController {
    @GetMapping(AdminEndpointPaths.ADMISSION_FILE)
    fun getAdmissionFile(): ResponseEntity<ApiResponse<AdmissionFileResponse>> =
        ResponseEntity.ok(ApiResponse(data = AdmissionFileResponse(downloadUrl = null, expiresAt = null)))
}
