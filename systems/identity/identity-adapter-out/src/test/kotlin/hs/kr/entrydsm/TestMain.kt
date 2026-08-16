package hs.kr.entrydsm.identity.adapterout

import hs.kr.entrydsm.identity.adapterout.security.BCryptPasswordHasher
import hs.kr.entrydsm.identity.adapterout.security.AesGcmPersonalDataEncryptor
import hs.kr.entrydsm.identity.adapterout.security.HmacLoginIdHasher
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterIntegrationTest
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterTest
import hs.kr.entrydsm.identity.adapterout.security.RedisDurabilityGuardTest
import hs.kr.entrydsm.identity.adapterout.persistence.AccountCommandPersistenceAdapterTest
import hs.kr.entrydsm.identity.adapterout.persistence.TransactionalAccountRegistrationAdapterTest
import hs.kr.entrydsm.identity.adapterout.repository.JpaAccountRepositoryAdapterIntegrationTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    IdentityAdapterOutSmokeTest::class,
    PersonalDataCryptoTest::class,
    AccountCommandPersistenceAdapterTest::class,
    TransactionalAccountRegistrationAdapterTest::class,
    JpaAccountRepositoryAdapterIntegrationTest::class,
    RedisRefreshTokenRotationAdapterTest::class,
    RedisDurabilityGuardTest::class,
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
        val hasher = BCryptPasswordHasher(10)
        val passwordHash = hasher.hash("Password1!")

        assertNotEquals("Password1!", passwordHash.value)
        assertTrue(hasher.matches("Password1!", passwordHash))
        assertFalse(hasher.matches("wrong-password", passwordHash))
    }
}

class PersonalDataCryptoTest {
    @Test
    fun loginIdHasherIsDeterministicWithoutStoringTheLoginId() {
        val hasher = HmacLoginIdHasher("test-login-id-hash-key")

        val hashed = hasher.hash("01012345678")

        assertNotEquals("01012345678", hashed)
        assertEquals(hashed, hasher.hash("01012345678"))
        assertTrue(hasher.isHash(hashed))
    }

    @Test
    fun personalDataEncryptorUsesAuthenticatedEncryption() {
        val encryptor = AesGcmPersonalDataEncryptor(
            keyBase64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
            legacyPlaintextReadEnabled = false,
        )

        val encrypted = encryptor.encrypt("홍길동")

        assertNotEquals("홍길동", encrypted)
        assertTrue(encryptor.isEncrypted(encrypted))
        assertEquals("홍길동", encryptor.decrypt(encrypted))
        assertNotEquals(encrypted, encryptor.encrypt("홍길동"))
    }

    @Test
    fun personalDataEncryptorCanReadLegacyPlaintextForMigration() {
        val encryptor = AesGcmPersonalDataEncryptor(
            keyBase64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
            legacyPlaintextReadEnabled = true,
        )

        assertFalse(encryptor.isEncrypted("홍길동"))
        assertEquals("홍길동", encryptor.decrypt("홍길동"))
    }
}
