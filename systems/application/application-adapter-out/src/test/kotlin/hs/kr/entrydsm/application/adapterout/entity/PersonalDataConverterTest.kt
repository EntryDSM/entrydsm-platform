package hs.kr.entrydsm.application.adapterout.entity

import hs.kr.entrydsm.common.crypto.PersonalDataCipher
import org.junit.Assert.*
import org.junit.Test

class PersonalDataConverterTest {
    private val key = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
    private val converter = PersonalDataConverter(key)

    @Test
    fun encryptsStoredValuesAndRestoresApiValues() {
        val encrypted = converter.convertToDatabaseColumn("홍길동")

        assertNotEquals("홍길동", encrypted)
        assertEquals("홍길동", converter.convertToEntityAttribute(encrypted))
        assertEquals("기존 평문", converter.convertToEntityAttribute("기존 평문"))
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `공통 모듈과 상호 운용하며 null 읽기와 잘못된 암호문 거부를 유지한다`() {
        val cipher = PersonalDataCipher(key)
        assertNull(converter.convertToEntityAttribute(null))
        assertEquals("홍길동", cipher.decrypt(converter.convertToDatabaseColumn("홍길동")))
        assertEquals("홍길동", converter.convertToEntityAttribute(cipher.encrypt("홍길동")))
        assertThrows(IllegalArgumentException::class.java) { converter.convertToEntityAttribute("v1.a.b") }
    }
}
