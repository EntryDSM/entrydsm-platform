package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.PassIdentity
import hs.kr.entrydsm.identity.application.port.out.PassCallbackTokenStore
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassProviderException
import hs.kr.entrydsm.identity.application.port.out.PassProviderPort
import hs.kr.entrydsm.identity.application.port.out.PassVerificationProof
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PassServiceTest {
    @Test
    fun savesVerifiedIdentityAsATemporaryProof() {
        val store = RecordingProofStore()
        val service = PassService(
            provider = provider(PassIdentity("01012345678", "홍길동")),
            proofStore = store,
            callbackTokenStore = AllowingCallbackTokenStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val result = service.verify("model-token")

        assertEquals("01012345678", result.phoneNumber)
        assertEquals(Triple("01012345678", "홍길동", 300L), store.saved)
    }

    @Test
    fun rejectsRedirectUrlFromAnUntrustedOrigin() {
        val service = PassService(
            provider = provider(PassIdentity("01012345678", "홍길동")),
            proofStore = RecordingProofStore(),
            callbackTokenStore = AllowingCallbackTokenStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val exception = assertThrows(IdentityDomainException::class.java) {
            service.generatePopup("https://auth.entrydsm.kr.evil.example/callback")
        }

        assertEquals(ErrorCode.INVALID_PASS_REDIRECT_URL, exception.errorCode)
    }

    @Test
    fun mapsProviderAvailabilityFailureWithoutExposingProviderDetails() {
        val service = PassService(
            provider = object : PassProviderPort {
                override fun generatePopup(redirectUrl: String): String =
                    throw PassProviderException(PassProviderException.Reason.UNAVAILABLE)

                override fun verify(token: String): PassIdentity =
                    throw PassProviderException(PassProviderException.Reason.UNAVAILABLE)
            },
            proofStore = RecordingProofStore(),
            callbackTokenStore = AllowingCallbackTokenStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val exception = assertThrows(IdentityDomainException::class.java) {
            service.generatePopup("https://auth.entrydsm.kr/callback")
        }

        assertEquals(ErrorCode.PASS_PROVIDER_UNAVAILABLE, exception.errorCode)
        assertEquals(ErrorCode.PASS_PROVIDER_UNAVAILABLE.message, exception.message)
    }

    @Test
    fun rejectsAProviderTokenThatWasAlreadyClaimed() {
        val service = PassService(
            provider = provider(PassIdentity("01012345678", "홍길동")),
            proofStore = RecordingProofStore(),
            callbackTokenStore = RejectingCallbackTokenStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val exception = assertThrows(IdentityDomainException::class.java) {
            service.verify("replayed-token")
        }

        assertEquals(ErrorCode.INVALID_PASS, exception.errorCode)
    }

    private fun provider(identity: PassIdentity) = object : PassProviderPort {
        override fun generatePopup(redirectUrl: String): String = "<form></form>"

        override fun verify(token: String): PassIdentity = identity
    }

    private class RecordingProofStore : PassProofStore {
        var saved: Triple<String, String, Long>? = null

        override fun save(phoneNumber: String, name: String, ttlSeconds: Long) {
            saved = Triple(phoneNumber, name, ttlSeconds)
        }

        override fun consume(phoneNumber: String): PassVerificationProof? = null
    }

    private object AllowingCallbackTokenStore : PassCallbackTokenStore {
        override fun claim(token: String, ttlSeconds: Long): Boolean = true
    }

    private object RejectingCallbackTokenStore : PassCallbackTokenStore {
        override fun claim(token: String, ttlSeconds: Long): Boolean = false
    }
}
