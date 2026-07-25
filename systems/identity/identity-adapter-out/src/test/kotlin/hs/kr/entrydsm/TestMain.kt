package hs.kr.entrydsm.identity.adapterout

import hs.kr.entrydsm.identity.adapterout.security.BCryptPasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityAdapterOutModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }

    @Test
    fun bcryptHasherStoresOnlyAHashAndVerifiesPasswords() {
        val hasher = BCryptPasswordHasher()
        val passwordHash = hasher.hash("Password1!")

        assertNotEquals("Password1!", passwordHash.value)
        assertTrue(hasher.matches("Password1!", passwordHash))
        assertFalse(hasher.matches("wrong-password", passwordHash))
    }
}
