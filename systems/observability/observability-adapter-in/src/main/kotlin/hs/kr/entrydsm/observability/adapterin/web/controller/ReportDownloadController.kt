package hs.kr.entrydsm.observability.adapterin.web.controller

import hs.kr.entrydsm.observability.application.port.out.ReportObjectStoragePort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 리포트 다운로드 URL이 가리키는 실제 파일 서빙. S3 presigned URL의 로컬 대체 구현. */
@RestController
class ReportDownloadController(
    private val reportObjectStoragePort: ReportObjectStoragePort,
) {
    @GetMapping("/api/monitor/v11/reports/download")
    fun download(@RequestParam token: String): ResponseEntity<ByteArray> {
        val downloaded = reportObjectStoragePort.resolve(token) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${downloaded.fileName}\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(downloaded.bytes)
    }
}
