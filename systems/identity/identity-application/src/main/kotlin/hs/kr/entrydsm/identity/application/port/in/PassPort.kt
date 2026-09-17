package hs.kr.entrydsm.identity.application.port.`in`

import hs.kr.entrydsm.identity.application.port.`in`.result.PassVerificationResult

interface PassPort {
    fun generatePopup(redirectUrl: String): String

    fun verify(token: String): PassVerificationResult
}

