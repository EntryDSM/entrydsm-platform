package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import java.time.Instant

/**
 * 비동기로 처리하는 내보내기 작업입니다.
 *
 * @property exportJobId 외부에 노출하는 작업 식별자
 * @property objectKey 완료된 산출물의 저장소 객체 키. 완료 전에는 null
 */
data class ExportJob(
    val id: Long? = null,
    val exportJobId: String,
    val type: ExportType,
    val status: ExportStatus,
    val filter: ApplicantFilter = ApplicantFilter(),
    val objectKey: String? = null,
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val failedCount: Int = 0,
) {
    fun started(startedAt: Instant? = this.startedAt): ExportJob =
        copy(status = ExportStatus.PROCESSING, startedAt = startedAt, failureCode = null, failureMessage = null, failedCount = 0)

    fun pending(): ExportJob = copy(status = ExportStatus.PENDING, startedAt = null, failureCode = null, failureMessage = null, failedCount = 0)

    fun withTotal(totalCount: Int): ExportJob = copy(totalCount = totalCount)

    fun processed(processedCount: Int): ExportJob = copy(processedCount = processedCount)

    fun completed(objectKey: String, completedAt: Instant): ExportJob =
        copy(status = ExportStatus.COMPLETED, objectKey = objectKey, completedAt = completedAt, failureCode = null, failureMessage = null, failedCount = 0)

    fun failed(completedAt: Instant, errorCode: ErrorCode = ErrorCode.INTERNAL_SERVER_ERROR,
        failedCount: Int = 0): ExportJob =
        copy(status = ExportStatus.FAILED, completedAt = completedAt,
            failureCode = errorCode.name, failureMessage = errorCode.message, failedCount = failedCount)
}
