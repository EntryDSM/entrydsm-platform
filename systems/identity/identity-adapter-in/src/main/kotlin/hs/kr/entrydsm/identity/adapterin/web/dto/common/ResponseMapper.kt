package hs.kr.entrydsm.identity.adapterin.web.dto.common

import hs.kr.entrydsm.identity.adapterin.web.dto.response.AccountResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.ApplicationResultResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.ApplicationStatusResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.BasicInfoResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.ProfileResponse
import hs.kr.entrydsm.identity.adapterin.web.dto.response.UserSummaryResponse
import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationResultResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.`in`.result.BasicInfoResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.port.`in`.result.UserSummaryResult

fun UserSummaryResult.toResponse(): UserSummaryResponse =
    UserSummaryResponse(
        userId = userId.toExternalUserId(),
        role = role.name,
        status = status,
    )

fun AccountResult.toResponse(): AccountResponse =
    AccountResponse(
        userId = userId.toExternalUserId(),
        role = role.name,
        status = status,
        profile = profile.toResponse(),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ProfileResult.toResponse(): ProfileResponse =
    ProfileResponse(
        name = name,
        phone = phone,
        birthdate = birthdate,
        signupType = signupType,
        applicantStatus = applicantStatus,
    )

fun BasicInfoResult.toResponse(): BasicInfoResponse =
    BasicInfoResponse(
        userId = userId.toExternalUserId(),
        role = role.name,
        status = status,
        name = name,
        phone = phone,
        birthdate = birthdate,
        signupType = signupType,
        applicantStatus = applicantStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ApplicationStatusResult.toResponse(): ApplicationStatusResponse =
    ApplicationStatusResponse(
        applicantStatus = applicantStatus,
        submittedAt = submittedAt,
        updatedAt = updatedAt,
    )

fun ApplicationResultResult.toResponse(): ApplicationResultResponse =
    ApplicationResultResponse(
        passStatus = passStatus,
        announcedAt = announcedAt,
    )

private fun Long.toExternalUserId(): String = "user_$this"
