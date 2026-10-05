package hs.kr.entrydsm.admin.domain.exception

import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeStackTraceTest {
    @Test
    fun `중첩된 원인의 타입과 위치를 기록하고 메시지는 제외한다`() {
        val root = IllegalArgumentException("비밀 원서 본문과 주소").apply {
            stackTrace = arrayOf(StackTraceElement("FormMapper", "read", "FormMapper.kt", 73))
        }
        val wrapped = AdminDomainException(ErrorCode.APPLICATION_FORM_INVALID, root)
        val stack = wrapped.safeStackTrace()
        assertTrue(stack.contains(AdminDomainException::class.java.name))
        assertTrue(stack.contains(IllegalArgumentException::class.java.name))
        assertTrue(stack.contains("FormMapper.read(FormMapper.kt:73)"))
        assertFalse(stack.contains("비밀 원서"))
        assertFalse(stack.contains(ErrorCode.APPLICATION_FORM_INVALID.message))
    }

    @Test
    fun `순환하는 원인 체인도 각 예외를 한 번만 기록한다`() {
        val first = Exception("첫 메시지").apply { stackTrace = emptyArray() }
        val second = Exception("두 번째 메시지", first).apply { stackTrace = emptyArray() }
        first.initCause(second)
        assertEquals("java.lang.Exception\n\nCaused by java.lang.Exception\n", first.safeStackTrace())
    }
}
