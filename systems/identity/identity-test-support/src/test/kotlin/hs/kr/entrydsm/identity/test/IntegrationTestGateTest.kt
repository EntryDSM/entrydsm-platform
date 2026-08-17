package hs.kr.entrydsm.identity.test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationTestGateTest {
    @Test
    fun acceptsTrueIgnoringCase() {
        assertTrue(IntegrationTestGate.isRequired { "true" })
        assertTrue(IntegrationTestGate.isRequired { "TRUE" })
    }

    @Test
    fun rejectsFalseAndMissingValues() {
        assertFalse(IntegrationTestGate.isRequired { "false" })
        assertFalse(IntegrationTestGate.isRequired { null })
    }
}
