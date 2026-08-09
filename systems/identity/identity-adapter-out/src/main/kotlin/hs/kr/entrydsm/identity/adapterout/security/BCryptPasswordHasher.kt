package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher(
    @Value("\${auth.password.bcrypt-strength:12}") strength: Int,
) : PasswordHasher {
    private val encoder = BCryptPasswordEncoder(strength)

    override fun hash(rawPassword: String): PasswordHash =
        PasswordHash.fromEncoded(requireNotNull(encoder.encode(rawPassword)))

    override fun matches(rawPassword: String, passwordHash: PasswordHash): Boolean =
        encoder.matches(rawPassword, passwordHash.value)
}
