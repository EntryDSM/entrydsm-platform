package hs.kr.entrydsm.identity.adapterout.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BCryptPasswordHasherTest {
    @Test
    fun hashesAndVerifiesWithoutKeepingTheRawPassword() {
        val hasher = BCryptPasswordHasher()
        val passwordHash = hasher.hash("Password1!")

        assertNotEquals("Password1!", passwordHash.value)
        assertTrue(hasher.matches("Password1!", passwordHash))
        assertFalse(hasher.matches("wrong-password", passwordHash))
    }
}
