package hs.kr.entrydsm.admin.adapterin.web.dto.common

import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantDetailResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantSummaryResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ExamineeNumberIssueResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.PageResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScoreResponse
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ExamineeNumberIssueResult
import hs.kr.entrydsm.admin.domain.model.Page

fun Applicant.toSummaryResponse(): ApplicantSummaryResponse = ApplicantSummaryResponse(
    applicantId = id,
    name = name,
    region = region,
    admissionType = admissionType,
    graduationStatus = graduationStatus,
    examineeNumber = examineeNumber,
    isArrived = isArrived,
    status = status,
)

fun Applicant.toDetailResponse(): ApplicantDetailResponse = ApplicantDetailResponse(
    applicantId = id,
    name = name,
    birthDate = birthDate,
    phoneNumber = phoneNumber,
    region = region,
    admissionType = admissionType,
    graduationStatus = graduationStatus,
    schoolName = schoolName,
    examineeNumber = examineeNumber,
    isArrived = isArrived,
    status = status,
    score = totalScore?.let(::ScoreResponse),
    submittedAt = submittedAt,
    arrivedAt = arrivedAt,
    updatedAt = updatedAt,
)

fun <T, R> Page<T>.toResponse(transform: (T) -> R): PageResponse<R> = PageResponse(
    items = items.map(transform),
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)

fun ExamineeNumberIssueResult.toResponse(): ExamineeNumberIssueResponse =
    ExamineeNumberIssueResponse(
        issuedCount = issuedCount,
        skippedCount = skippedCount,
        totalTargets = totalTargets,
    )
