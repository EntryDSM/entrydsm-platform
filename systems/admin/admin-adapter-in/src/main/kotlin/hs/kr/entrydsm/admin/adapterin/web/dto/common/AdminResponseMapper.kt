package hs.kr.entrydsm.admin.adapterin.web.dto.common

import hs.kr.entrydsm.admin.adapterin.web.dto.response.CreateExportResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ExportJobResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.NoticeResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.QuestionAnswerResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScorePolicyResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScoreWeightsResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScreeningResultResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.StatisticsResponse
import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.ApplicantStatistics
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.ExportJobView
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.model.ScreeningResult

fun ScorePolicy.toResponse(): ScorePolicyResponse = ScorePolicyResponse(
    policyVersion = policyVersion,
    weights = ScoreWeightsResponse(
        subject = weights.subject,
        attendance = weights.attendance,
        volunteer = weights.volunteer,
    ),
    roundingScale = roundingScale,
    effectiveFrom = effectiveFrom,
    updatedBy = updatedBy,
)

fun ScreeningResult.toResponse(): ScreeningResultResponse = ScreeningResultResponse(
    dryRun = dryRun,
    passCount = passCount,
    failCount = failCount,
    excludedCount = excludedCount,
    processedAt = processedAt,
)

/**
 * 요청한 지표만 담아 명세의 `metrics` 맵 형태로 만듭니다.
 */
fun ApplicantStatistics.toResponse(): StatisticsResponse = StatisticsResponse(
    generatedAt = generatedAt,
    metrics = buildMap {
        applicantCount?.let {
            put(
                StatisticsMetric.APPLICANT_COUNT.name,
                mapOf(
                    "total" to it.total,
                    "byType" to it.byType.mapKeys { (type, _) -> type.name },
                ),
            )
        }
        competitionRate?.let {
            put(
                StatisticsMetric.COMPETITION_RATE.name,
                it.mapKeys { (type, _) -> type.name },
            )
        }
        regionDistribution?.let {
            put(
                StatisticsMetric.REGION_DISTRIBUTION.name,
                it.mapKeys { (region, _) -> region.name },
            )
        }
        typeDistribution?.let {
            put(
                StatisticsMetric.TYPE_DISTRIBUTION.name,
                it.mapKeys { (type, _) -> type.name },
            )
        }
        dailyTrend?.let { points ->
            put(
                StatisticsMetric.DAILY_TREND.name,
                points.map { mapOf("date" to it.date.toString(), "count" to it.count) },
            )
        }
    },
)

fun ExportJob.toCreateResponse(): CreateExportResponse = CreateExportResponse(
    exportJobId = exportJobId,
    status = status,
)

fun ExportJobView.toResponse(): ExportJobResponse = ExportJobResponse(
    exportJobId = job.exportJobId,
    type = job.type,
    status = job.status,
    downloadUrl = download?.downloadUrl,
    expiresAt = download?.expiresAt,
    createdAt = job.createdAt,
    completedAt = job.completedAt,
)

fun Notice.toResponse(): NoticeResponse = NoticeResponse(
    noticeId = id,
    title = title,
    isPinned = isPinned,
    createdAt = createdAt,
)

fun QuestionAnswer.toResponse(): QuestionAnswerResponse = QuestionAnswerResponse(
    answerId = id,
    questionId = questionId,
    content = content,
    answeredAt = answeredAt,
)
