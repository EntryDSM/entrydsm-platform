package hs.kr.entrydsm.identity.adapterin.web

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.request.PassPopupRequest
import hs.kr.entrydsm.identity.adapterin.web.dto.response.PassVerificationResponse
import hs.kr.entrydsm.identity.application.port.`in`.PassPort
import hs.kr.entrydsm.identity.application.web.AuthEndpointPaths
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(AuthEndpointPaths.BASE + AuthEndpointPaths.PASS_PATH)
class PassController(
    private val passPort: PassPort,
) {
    @PostMapping(AuthEndpointPaths.PASS_POPUP_PATH, produces = [MediaType.TEXT_HTML_VALUE])
    fun popup(
        @Valid @RequestBody request: PassPopupRequest,
    ): ResponseEntity<String> = ResponseEntity
        .ok()
        .contentType(MediaType.TEXT_HTML)
        .body(passPort.generatePopup(request.redirectUrl))

    @GetMapping(AuthEndpointPaths.PASS_INFO_PATH)
    fun info(
        @RequestParam("mdl_tkn") token: String,
    ): ApiResponse<PassVerificationResponse> =
        ApiResponse(
            data = passPort.verify(token).let {
                PassVerificationResponse(it.phoneNumber, it.name)
            },
        )
}
