package hs.kr.entrydsm.identity.adapterout

import hs.kr.entrydsm.identity.adapterout.security.BCryptPasswordHasher
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterIntegrationTest
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterTest
import hs.kr.entrydsm.identity.adapterout.persistence.AccountCommandPersistenceAdapterTest
import hs.kr.entrydsm.identity.adapterout.persistence.TransactionalAccountRegistrationAdapterTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    IdentityAdapterOutSmokeTest::class,
    AccountCommandPersistenceAdapterTest::class,
    TransactionalAccountRegistrationAdapterTest::class,
    RedisRefreshTokenRotationAdapterTest::class,
    RedisRefreshTokenRotationAdapterIntegrationTest::class,
)
class IdentityAdapterOutModuleTest

class IdentityAdapterOutSmokeTest {
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
