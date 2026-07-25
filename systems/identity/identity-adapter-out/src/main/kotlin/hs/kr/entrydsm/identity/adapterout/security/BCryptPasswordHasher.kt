package hs.kr.entrydsm.identity.adapterout.security

import hs.kr.entrydsm.identity.application.port.out.PasswordHasher
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class BCryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(rawPassword: String): PasswordHash =
        PasswordHash.fromEncoded(requireNotNull(encoder.encode(rawPassword)))

    override fun matches(rawPassword: String, passwordHash: PasswordHash): Boolean =
        encoder.matches(rawPassword, passwordHash.value)
}
