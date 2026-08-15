package hs.kr.entrydsm.admin.domain.model

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
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
    val createdAt: Instant,
    val completedAt: Instant? = null,
) {
    fun started(): ExportJob = copy(status = ExportStatus.PROCESSING)

    fun completed(objectKey: String, completedAt: Instant): ExportJob =
        copy(status = ExportStatus.COMPLETED, objectKey = objectKey, completedAt = completedAt)

    fun failed(completedAt: Instant): ExportJob =
        copy(status = ExportStatus.FAILED, completedAt = completedAt)
}
