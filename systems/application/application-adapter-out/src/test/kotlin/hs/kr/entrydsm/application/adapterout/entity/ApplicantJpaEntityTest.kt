package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.application.domain.model.Applicant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicantJpaEntityTest {
    @Test
    fun fromCreatesNewEntityWithoutCarryingPositiveDomainId() {
        val entity = ApplicantJpaEntity.from(
            Applicant(
                id = 100L,
                accountId = 1L,
            ),
        )

        assertNull(entity.id)
        assertEquals(1L, entity.accountId)
    }

    @Test
    fun updateFromDoesNotOverwriteCreatedAt() {
        val originalCreatedAt = LocalDateTime.of(2026, 1, 1, 0, 0)
        val domainCreatedAt = LocalDateTime.of(2026, 2, 1, 0, 0)
        val entity = ApplicantJpaEntity(
            id = 1L,
            accountId = 1L,
            createdAt = originalCreatedAt,
        )

        entity.updateFrom(
            Applicant(
                id = 1L,
                accountId = 2L,
                createdAt = domainCreatedAt,
            ),
        )

        assertEquals(originalCreatedAt, entity.createdAt)
        assertEquals(2L, entity.accountId)
    }

    @Test
    fun fromUsesDomainCreatedAtForNewEntity() {
        val createdAt = LocalDateTime.of(2026, 3, 1, 0, 0)

        val entity = ApplicantJpaEntity.from(
            Applicant(
                id = 0L,
                accountId = 1L,
                createdAt = createdAt,
            ),
        )

        assertEquals(createdAt, entity.createdAt)
    }
}
