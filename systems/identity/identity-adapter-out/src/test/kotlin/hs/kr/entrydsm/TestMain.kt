package hs.kr.entrydsm.identity.adapterout

import hs.kr.entrydsm.identity.adapterout.security.BCryptPasswordHasher
import hs.kr.entrydsm.identity.adapterout.security.AesGcmPersonalDataEncryptor
import hs.kr.entrydsm.identity.adapterout.security.AccountPasswordResetOwnershipVerifierTest
import hs.kr.entrydsm.identity.adapterout.security.AccountSignupOwnershipVerifierTest
import hs.kr.entrydsm.identity.adapterout.pass.KcbLicenseFileInitializerTest
import hs.kr.entrydsm.identity.adapterout.pass.KcbPassProviderAdapterTest
import hs.kr.entrydsm.identity.adapterout.security.HmacLoginIdHasher
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterIntegrationTest
import hs.kr.entrydsm.identity.adapterout.security.RedisRefreshTokenRotationAdapterTest
import hs.kr.entrydsm.identity.adapterout.security.RedisDurabilityGuardTest
import hs.kr.entrydsm.identity.adapterout.persistence.AccountCommandPersistenceAdapterTest
import hs.kr.entrydsm.identity.adapterout.persistence.TransactionalAccountRegistrationAdapterTest
import hs.kr.entrydsm.identity.adapterout.repository.JpaAccountRepositoryAdapterIntegrationTest
import hs.kr.entrydsm.identity.adapterout.persistence.PersistenceProfileContractTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import javax.crypto.AEADBadTagException

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
    AccountPasswordResetOwnershipVerifierTest::class,
    AccountSignupOwnershipVerifierTest::class,
    KcbLicenseFileInitializerTest::class,
    KcbPassProviderAdapterTest::class,
    PersistenceProfileContractTest::class,
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
        )

        val encrypted = encryptor.encrypt("홍길동")

        assertNotEquals("홍길동", encrypted)
        assertTrue(encryptor.isEncrypted(encrypted))
        assertEquals("홍길동", encryptor.decrypt(encrypted))
        assertNotEquals(encrypted, encryptor.encrypt("홍길동"))

        val parts = encrypted.split('.').toMutableList()
        val tamperedCiphertext = java.util.Base64.getUrlDecoder().decode(parts[2]).also { it[0] = (it[0].toInt() xor 1).toByte() }
        parts[2] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedCiphertext)
        assertThrows(AEADBadTagException::class.java) {
            encryptor.decrypt(parts.joinToString("."))
        }
    }

    @Test
    fun personalDataEncryptorRejectsLegacyPlaintext() {
        val encryptor = AesGcmPersonalDataEncryptor(
            keyBase64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
        )

        assertFalse(encryptor.isEncrypted("홍길동"))
        assertThrows(IllegalArgumentException::class.java) {
            encryptor.decrypt("홍길동")
        }
    }

    @Test
    fun personalDataSecurityKeysRejectMissingOrMalformedValues() {
        assertThrows(IllegalArgumentException::class.java) {
            HmacLoginIdHasher("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmPersonalDataEncryptor(keyBase64 = "not-base64")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AesGcmPersonalDataEncryptor(keyBase64 = "")
        }
    }
}
