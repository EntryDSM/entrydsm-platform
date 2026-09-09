package hs.kr.entrydsm.observability.application.port.out

import java.time.Instant

interface ReportObjectStoragePort {
    fun store(fileName: String, bytes: ByteArray): StoredReport

    fun resolve(token: String): DownloadedReport?
}

data class StoredReport(val downloadUrl: String, val expiresAt: Instant)

data class DownloadedReport(val fileName: String, val bytes: ByteArray)
