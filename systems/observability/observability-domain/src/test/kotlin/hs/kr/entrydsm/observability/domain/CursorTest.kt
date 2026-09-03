package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CursorTest {
    @Test
    fun encodesAndDecodesRoundTrip() {
        val cursor = Cursor(lastScore = 1753679005000, lastId = "7d1b04a2")

        val decoded = Cursor.decode(cursor.encode())

        assertEquals(cursor, decoded)
    }

    @Test
    fun rejectsCorruptedCursor() {
        assertThrows(MonitorDomainException::class.java) {
            Cursor.decode("not-a-valid-cursor!!")
        }
    }
}
