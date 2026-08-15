package hs.kr.entrydsm.admin.adapterin.web.dto.response

import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import java.time.Instant

data class ScorePolicyResponse(
    val policyVersion: Int,
    val weights: ScoreWeightsResponse,
    val roundingScale: Int,
    val effectiveFrom: Instant,
    val updatedBy: String,
)

data class ScoreWeightsResponse(
    val subject: Double,
    val attendance: Double,
    val volunteer: Double,
)

data class ScreeningResultResponse(
    val dryRun: Boolean,
    val passCount: Int,
    val failCount: Int,
    val excludedCount: Int,
    val processedAt: Instant,
)

data class StatisticsResponse(
    val generatedAt: Instant,
    val metrics: Map<String, Any>,
)

data class CreateExportResponse(
    val exportJobId: String,
    val status: ExportStatus,
)

data class ExportJobResponse(
    val exportJobId: String,
    val type: ExportType,
    val status: ExportStatus,
    val downloadUrl: String?,
    val expiresAt: Instant?,
    val createdAt: Instant,
    val completedAt: Instant?,
)

data class NoticeResponse(
    val noticeId: Long?,
    val title: String,
    val isPinned: Boolean,
    val createdAt: Instant?,
)

data class QuestionAnswerResponse(
    val answerId: Long?,
    val questionId: Long,
    val content: String,
    val answeredAt: Instant?,
)
