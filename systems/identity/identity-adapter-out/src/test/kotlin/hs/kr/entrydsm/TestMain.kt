package hs.kr.entrydsm.identity.adapterout

import hs.kr.entrydsm.identity.adapterout.security.BCryptPasswordHasherTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    BCryptPasswordHasherTest::class,
)
class IdentityAdapterOutModuleTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }
}
