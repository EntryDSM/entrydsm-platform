package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationSnapshot

interface ApplicationDataPort {
    fun create(userId: String, updatedAt: java.time.Instant): ApplicationSnapshot

    fun findByUserId(userId: String): ApplicationSnapshot?

    fun cancel(userId: String, reason: String?, updatedAt: java.time.Instant): ApplicationSnapshot
}
