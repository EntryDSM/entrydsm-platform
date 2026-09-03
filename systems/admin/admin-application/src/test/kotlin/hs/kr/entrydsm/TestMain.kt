package hs.kr.entrydsm.admin.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminApplicationModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun `CSV 칸은 쉼표와 줄바꿈을 따옴표 안에 그대로 보존한다`() {
        assertEquals("\"김,시흔\"", toCsvField("김,시흔"))
        assertEquals("\"두\n줄\"", toCsvField("두\n줄"))
    }

    @Test
    fun `CSV 칸의 따옴표는 두 번 겹쳐 이스케이프한다`() {
        assertEquals("\"이름 \"\"별명\"\"\"", toCsvField("이름 \"별명\""))
    }

    @Test
    fun `스프레드시트가 수식으로 읽는 선두 문자는 무력화한다`() {
        assertEquals("\"'=1+1\"", toCsvField("=1+1"))
        assertEquals("\"'@SUM(A1)\"", toCsvField("@SUM(A1)"))
        assertEquals("\"'-1\"", toCsvField("-1"))
    }

    @Test
    fun `null은 빈 칸으로 낸다`() {
        assertEquals("\"\"", toCsvField(null))
    }
}
