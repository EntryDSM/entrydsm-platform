package hs.kr.entrydsm.identity.application.service

import hs.kr.entrydsm.identity.application.port.`in`.PassPort
import hs.kr.entrydsm.identity.application.port.`in`.PassVerificationResult
import hs.kr.entrydsm.identity.application.port.out.PassIdentity
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import hs.kr.entrydsm.identity.application.port.out.PassProviderException
import hs.kr.entrydsm.identity.application.port.out.PassProviderPort
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import java.net.URI

class PassService(
    private val provider: PassProviderPort,
    private val proofStore: PassProofStore,
    private val proofTtlSeconds: Long,
    allowedRedirectOrigins: String,
) : PassPort {
    private val allowedOrigins = allowedRedirectOrigins
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(::parseOrigin)
        .toSet()

    init {
        require(proofTtlSeconds > 0) { "PASS proof TTL must be positive." }
        require(allowedOrigins.isNotEmpty()) { "At least one PASS redirect origin is required." }
    }

    override fun generatePopup(redirectUrl: String): String {
        if (!isAllowedRedirectUrl(redirectUrl)) {
            throw IdentityDomainException(ErrorCode.INVALID_PASS_REDIRECT_URL)
        }
        return try {
            provider.generatePopup(redirectUrl)
        } catch (exception: PassProviderException) {
            throw exception.toIdentityException()
        }
    }

    override fun verify(token: String): PassVerificationResult {
        if (token.isBlank() || token.length > MAX_TOKEN_LENGTH) {
            throw IdentityDomainException(ErrorCode.INVALID_PASS)
        }
        val identity = try {
            provider.verify(token)
        } catch (exception: PassProviderException) {
            throw exception.toIdentityException()
        }
        if (identity.phoneNumber.isBlank() || identity.name.isBlank()) {
            throw IdentityDomainException(ErrorCode.INVALID_PASS)
        }
        try {
            if (!proofStore.saveForToken(token, identity.phoneNumber, identity.name, proofTtlSeconds)) {
                throw IdentityDomainException(ErrorCode.INVALID_PASS)
            }
        } catch (exception: IdentityDomainException) {
            throw exception
        } catch (exception: RuntimeException) {
            throw IdentityDomainException(ErrorCode.PASS_PROOF_STORE_UNAVAILABLE, exception)
        }
        return identity.toResult()
    }

    private fun isAllowedRedirectUrl(value: String): Boolean {
        return try {
            val uri = URI(value)
            if (uri.userInfo != null || uri.host.isNullOrBlank() || uri.path == null) {
                false
            } else {
                allowedOrigins.any { origin ->
                    uri.scheme.equals(origin.scheme, ignoreCase = true) &&
                        uri.host.equals(origin.host, ignoreCase = true) &&
                        effectivePort(uri) == effectivePort(origin)
                }
            }
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun parseOrigin(value: String): URI {
        val uri = try { URI(value) } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid PASS redirect origin.", exception)
        }
        require(uri.scheme == "http" || uri.scheme == "https")
        require(uri.userInfo == null && !uri.host.isNullOrBlank())
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        return uri
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    private fun PassIdentity.toResult() = PassVerificationResult(phoneNumber, name)

    private fun PassProviderException.toIdentityException(): IdentityDomainException =
        IdentityDomainException(
            when (reason) {
                PassProviderException.Reason.INVALID_RESPONSE -> ErrorCode.INVALID_PASS
                PassProviderException.Reason.UNAVAILABLE -> ErrorCode.PASS_PROVIDER_UNAVAILABLE
            },
            this,
        )

    private companion object {
        const val MAX_TOKEN_LENGTH = 512
    }
}
