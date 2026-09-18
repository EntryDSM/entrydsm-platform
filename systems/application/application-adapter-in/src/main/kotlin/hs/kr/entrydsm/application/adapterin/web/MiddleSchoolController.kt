package hs.kr.entrydsm.application.adapterin.web

import hs.kr.entrydsm.application.adapterin.web.dto.common.ApiResponse
import hs.kr.entrydsm.application.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.application.adapterin.web.dto.response.MiddleSchoolSearchResponse
import hs.kr.entrydsm.application.application.port.`in`.MiddleSchoolPort
import hs.kr.entrydsm.application.application.port.`in`.command.SearchMiddleSchoolCommand
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/application/v11/middle-school")
class MiddleSchoolController(
    private val middleSchoolPort: MiddleSchoolPort,
) {
    @GetMapping
    fun getMiddleSchools(
        @RequestParam("name", defaultValue = "") name: String,
    ): ApiResponse<MiddleSchoolSearchResponse> {
        val result = middleSchoolPort.getMiddleSchools(
            SearchMiddleSchoolCommand(
                name = name.trim(),
            ),
        )
        return ApiResponse(data = result.toResponse())
    }
}
