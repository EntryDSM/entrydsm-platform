package hs.kr.entrydsm.application.administration.adapterin.document

import hs.kr.entrydsm.application.administration.adapterin.common.ApiResponse
import hs.kr.entrydsm.application.administration.adapterin.document.dto.FileResponse
import hs.kr.entrydsm.application.administration.domain.document.port.`in`.RegistrationFileUseCase
import hs.kr.entrydsm.configuration.domain.document.Requester
import org.springframework.web.bind.annotation.*

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["application.administration.enabled"], havingValue = "true")
@RequestMapping("/api/document/v11")
class RegistrationFileController(private val fileUseCase: RegistrationFileUseCase) {
    @GetMapping("/registration-documents/latest")
    fun findRegistrationDocument(
        @RequestAttribute(REQUESTER_ATTRIBUTE) requester: Requester,
    ): ApiResponse<FileResponse> = ApiResponse.success(FileResponse.of(fileUseCase.findRegistrationDocument(requester)))

}
