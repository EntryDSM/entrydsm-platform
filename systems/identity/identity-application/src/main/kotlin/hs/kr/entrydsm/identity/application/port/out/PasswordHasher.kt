package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.PasswordHash

interface PasswordHasher {
    fun hash(rawPassword: String): PasswordHash

    fun matches(rawPassword: String, passwordHash: PasswordHash): Boolean
}
