package hs.kr.entrydsm.admin.adapterin.web.dto.common

import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantDetailResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ApplicantSummaryResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ExamineeNumberIssueResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.PageResponse
import hs.kr.entrydsm.admin.adapterin.web.dto.response.ScoreResponse
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.model.ApplicantDetail
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

fun ApplicantDetail.toDetailResponse(): ApplicantDetailResponse = ApplicantDetailResponse(
    applicantId = applicant.id,
    name = applicant.name,
    birthDate = applicant.birthDate,
    phoneNumber = applicant.phoneNumber,
    region = applicant.region,
    admissionType = applicant.admissionType,
    graduationStatus = applicant.graduationStatus,
    schoolName = applicant.schoolName,
    examineeNumber = applicant.examineeNumber,
    isArrived = applicant.isArrived,
    status = applicant.status,
    score = score?.let {
        ScoreResponse(
            subjectScore = it.subjectScore,
            attendanceScore = it.attendanceScore,
            volunteerScore = it.volunteerScore,
            additionalScore = it.additionalScore,
            totalScore = it.totalScore,
        )
    },
    submittedAt = applicant.submittedAt,
    arrivedAt = applicant.arrivedAt,
    updatedAt = applicant.updatedAt,
    photoFileId = photoFileId,
    introduction = introduction,
    studyPlan = studyPlan,
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
