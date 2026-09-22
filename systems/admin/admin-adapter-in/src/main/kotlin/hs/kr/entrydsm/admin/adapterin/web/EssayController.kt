package hs.kr.entrydsm.admin.adapterin.web

import hs.kr.entrydsm.admin.domain.port.`in`.DownloadEssaysUseCase
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.nio.charset.StandardCharsets

@RestController
class EssayController(private val downloadEssaysUseCase: DownloadEssaysUseCase) {
    @GetMapping(AdminEndpointPaths.ESSAYS)
    fun download(): ResponseEntity<StreamingResponseBody> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("자기소개서_학업계획서.zip", StandardCharsets.UTF_8).build().toString(),
        )
        .body(StreamingResponseBody(downloadEssaysUseCase::writeTo))
}
