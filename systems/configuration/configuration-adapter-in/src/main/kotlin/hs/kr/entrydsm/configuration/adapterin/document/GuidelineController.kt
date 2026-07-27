package hs.kr.entrydsm.configuration.adapterin.document

import hs.kr.entrydsm.configuration.adapterin.common.ApiResponse
import hs.kr.entrydsm.configuration.adapterin.document.dto.DownloadUrlResponse
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.port.`in`.IssueDownloadUrlUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val CATEGORY = FileCategory.GUIDELINE

@RestController
@RequestMapping("/api/document/v11/guideline")
class GuidelineController(
    private val issueDownloadUrlUseCase: IssueDownloadUrlUseCase,
) {

    @GetMapping("/download")
    fun download(@RequestParam("guidelineId") guidelineId: String): ApiResponse<DownloadUrlResponse> =
        ApiResponse.success(
            DownloadUrlResponse.from(
                issueDownloadUrlUseCase.issueById(FileReferenceId.parse(CATEGORY, guidelineId))
            )
        )
}
