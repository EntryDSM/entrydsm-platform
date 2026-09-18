package hs.kr.entrydsm.application.application.port.`in`.command

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchMiddleSchoolCommandTest {

    @Test
    fun validNameCreatesCommand() {
        val command = SearchMiddleSchoolCommand(name = "대전")

        assertEquals("대전", command.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankNameThrowsException() {
        SearchMiddleSchoolCommand(name = "   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyNameThrowsException() {
        SearchMiddleSchoolCommand(name = "")
    }
}