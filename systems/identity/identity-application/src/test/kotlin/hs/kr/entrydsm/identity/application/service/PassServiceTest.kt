package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.out.PassIdentity
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
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val result = service.verify("model-token")

        assertEquals("01012345678", result.phoneNumber)
        assertEquals(
            StoredProof("model-token", "01012345678", "홍길동", 300),
            store.saved,
        )
    }

    @Test
    fun rejectsRedirectUrlFromAnUntrustedOrigin() {
        val service = PassService(
            provider = provider(PassIdentity("01012345678", "홍길동")),
            proofStore = RecordingProofStore(),
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
            proofStore = RejectingProofStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val exception = assertThrows(IdentityDomainException::class.java) {
            service.verify("replayed-token")
        }

        assertEquals(ErrorCode.INVALID_PASS, exception.errorCode)
    }

    @Test
    fun mapsProofStoreFailureToServiceUnavailable() {
        val service = PassService(
            provider = provider(PassIdentity("01012345678", "홍길동")),
            proofStore = ThrowingProofStore,
            proofTtlSeconds = 300,
            allowedRedirectOrigins = "https://auth.entrydsm.kr",
        )

        val exception = assertThrows(IdentityDomainException::class.java) {
            service.verify("model-token")
        }

        assertEquals(ErrorCode.PASS_PROOF_STORE_UNAVAILABLE, exception.errorCode)
    }

    private fun provider(identity: PassIdentity) = object : PassProviderPort {
        override fun generatePopup(redirectUrl: String): String = "<form></form>"

        override fun verify(token: String): PassIdentity = identity
    }

    private class RecordingProofStore : PassProofStore {
        var saved: StoredProof? = null

        override fun saveForToken(token: String, phoneNumber: String, name: String, ttlSeconds: Long): Boolean {
            saved = StoredProof(token, phoneNumber, name, ttlSeconds)
            return true
        }

        override fun consume(phoneNumber: String, name: String): PassVerificationProof? = null
    }

    private object ThrowingProofStore : PassProofStore {
        override fun saveForToken(token: String, phoneNumber: String, name: String, ttlSeconds: Long): Boolean {
            throw IllegalStateException("redis unavailable")
        }

        override fun consume(phoneNumber: String, name: String): PassVerificationProof? = null
    }

    private data class StoredProof(
        val token: String,
        val phoneNumber: String,
        val name: String,
        val ttlSeconds: Long,
    )

    private object RejectingProofStore : PassProofStore {
        override fun saveForToken(token: String, phoneNumber: String, name: String, ttlSeconds: Long): Boolean = false

        override fun consume(phoneNumber: String, name: String): PassVerificationProof? = null
    }
}
