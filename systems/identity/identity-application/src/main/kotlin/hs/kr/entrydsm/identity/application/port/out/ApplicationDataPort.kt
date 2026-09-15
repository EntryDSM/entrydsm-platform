package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot
import java.time.Instant

interface ApplicationDataPort {
    fun create(userId: Long, updatedAt: Instant): ApplicationSnapshot

    fun findByUserId(userId: Long): ApplicationSnapshot?

    fun cancel(userId: Long, reason: String?, updatedAt: Instant): ApplicationSnapshot
}
