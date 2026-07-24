package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.result.AccountResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ApplicationStatusResult
import hs.kr.entrydsm.identity.application.port.`in`.result.ProfileResult
import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import hs.kr.entrydsm.identity.domain.model.Account

internal fun Account.toAccountResult(): AccountResult =
    AccountResult(
        userId = userId,
        role = role,
        status = status,
        profile = ProfileResult(
            name = profile.name,
            phone = profile.phone,
            birthdate = profile.birthdate,
            signupType = profile.signupType,
            applicantStatus = profile.applicantStatus,
        ),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun ApplicationSnapshot.toStatusResult(): ApplicationStatusResult =
    ApplicationStatusResult(applicantStatus, submittedAt, updatedAt)
