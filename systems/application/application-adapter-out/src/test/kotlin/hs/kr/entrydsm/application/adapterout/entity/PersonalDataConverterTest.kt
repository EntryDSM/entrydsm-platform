package hs.kr.entrydsm.application.adapterout.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalDataConverterTest {
    private val converter = PersonalDataConverter("MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")

    @Test
    fun encryptsStoredValuesAndRestoresApiValues() {
        val encrypted = converter.convertToDatabaseColumn("홍길동")

        assertNotEquals("홍길동", encrypted)
        assertEquals("홍길동", converter.convertToEntityAttribute(encrypted))
        assertEquals("기존 평문", converter.convertToEntityAttribute("기존 평문"))
        assertNull(converter.convertToDatabaseColumn(null))
    }
}
