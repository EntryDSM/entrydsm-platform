package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.`in`.command.PasswordResetCommand
import hs.kr.entrydsm.identity.application.port.out.AccountQueryPort
import hs.kr.entrydsm.identity.application.port.out.PasswordResetOwnershipVerifier
import hs.kr.entrydsm.identity.application.port.out.PassProofStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Verifies the identity data supplied by the PASS flow and limits repeated attempts.
 * The PASS adapter can replace this port without changing the reset controller.
 */
@Component
class AccountPasswordResetOwnershipVerifier(
    private val accountQueryPort: AccountQueryPort,
    @Value("\${auth.password-reset.max-attempts:5}") private val maxAttempts: Int,
    @Value("\${auth.password-reset.window-seconds:900}") private val windowSeconds: Long,
    private val clock: Clock = Clock.systemUTC(),
    @Value("\${auth.password-reset.max-tracked-login-ids:10000}") private val maxTrackedLoginIds: Int = MAX_TRACKED_LOGIN_IDS,
    private val passProofStore: PassProofStore,
) : PasswordResetOwnershipVerifier {
    private val attemptsByLoginId = ConcurrentHashMap<String, AttemptWindow>()

    init {
        require(maxAttempts > 0) { "Password reset max attempts must be positive" }
        require(windowSeconds > 0) { "Password reset window must be positive" }
        require(maxTrackedLoginIds > 0) { "Password reset tracked login ID limit must be positive" }
    }

    override fun verify(command: PasswordResetCommand): Boolean {
        if (!allowAttempt(command.loginId)) return false
        val account = accountQueryPort.findByLoginId(command.loginId) ?: return false
        if (account.profile.name != command.name || account.profile.birthdate != command.birthdate) return false
        return passProofStore.consume(command.loginId, command.name) != null
    }

    private fun allowAttempt(loginId: String): Boolean {
        if (loginId.isBlank()) return false
        val now = Instant.now(clock)
        synchronized(attemptsByLoginId) {
            attemptsByLoginId.entries.removeIf {
                Duration.between(it.value.startedAt, now).seconds >= windowSeconds
            }
            val current = attemptsByLoginId[loginId]
            if (current == null && attemptsByLoginId.size >= maxTrackedLoginIds) return false
            val window = current?.copy(attempts = current.attempts + 1)
                ?: AttemptWindow(now, 1)
            attemptsByLoginId[loginId] = window
            return window.attempts <= maxAttempts
        }
    }

    private data class AttemptWindow(
        val startedAt: Instant,
        val attempts: Int,
    )

    private companion object {
        const val MAX_TRACKED_LOGIN_IDS = 10_000
    }
}
