package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot

interface ApplicationDataPort {
    fun create(userId: Long, updatedAt: java.time.Instant): ApplicationSnapshot

    fun findByUserId(userId: Long): ApplicationSnapshot?

    fun cancel(userId: Long, reason: String?, updatedAt: java.time.Instant): ApplicationSnapshot
}
