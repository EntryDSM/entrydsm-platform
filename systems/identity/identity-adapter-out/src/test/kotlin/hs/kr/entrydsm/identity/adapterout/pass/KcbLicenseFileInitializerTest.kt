package hs.kr.entrydsm.identity.adapterout.pass

import org.junit.Assert.assertThrows
import org.junit.Test

class KcbLicenseFileInitializerTest {
    @Test
    fun rejectsNonHttpsLicenseUrl() {
        val initializer = KcbLicenseFileInitializer(
            licenseFileUrl = "http://license.example/license.dat",
            licenseFilePath = "build/test-license.dat",
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
        )

        assertThrows(IllegalArgumentException::class.java) { initializer.initialize() }
    }

    @Test
    fun rejectsNonPositiveTimeouts() {
        assertThrows(IllegalArgumentException::class.java) {
            KcbLicenseFileInitializer("", "build/test-license.dat", 0, 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KcbLicenseFileInitializer("", "build/test-license.dat", 1000, 0)
        }
    }
}
