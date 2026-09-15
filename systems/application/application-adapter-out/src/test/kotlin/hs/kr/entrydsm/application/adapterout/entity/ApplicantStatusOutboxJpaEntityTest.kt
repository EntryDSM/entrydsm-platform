package hs.kr.entrydsm.application.adapterout.entity

import jakarta.persistence.Column
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicantStatusOutboxJpaEntityTest {
    @Test
    fun payloadMatchesFlywayLongBlobColumn() {
        val column = ApplicantStatusOutboxJpaEntity::class.java
            .getDeclaredField("payload")
            .getAnnotation(Column::class.java)

        assertEquals("LONGBLOB", column.columnDefinition)
    }
}
