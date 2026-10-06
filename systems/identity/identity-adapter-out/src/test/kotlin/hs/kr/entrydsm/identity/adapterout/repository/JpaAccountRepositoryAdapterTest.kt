package hs.kr.entrydsm.identity.adapterout.repository

import hs.kr.entrydsm.identity.application.port.out.LoginIdHasher
import hs.kr.entrydsm.identity.application.port.out.PersonalDataEncryptor
import hs.kr.entrydsm.identity.domain.enum.ErrorCode
import hs.kr.entrydsm.identity.domain.exception.IdentityDomainException
import hs.kr.entrydsm.identity.domain.model.PasswordHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import java.time.Instant

class JpaAccountRepositoryAdapterTest {
    private val repository = mock(AccountJpaRepository::class.java)
    private val adapter = JpaAccountRepositoryAdapter(repository, mock(StudentProfileJpaRepository::class.java),
        mock(LoginIdHasher::class.java), mock(PersonalDataEncryptor::class.java))
    private val oldHash = PasswordHash.fromEncoded("old-hash")
    private val newHash = PasswordHash.fromEncoded("new-hash")

    @Test
    fun passwordAndRevocationUseOneDatabaseUpdate() {
        `when`(repository.changePasswordAndRevoke(eq(123L), (eq(oldHash.value) ?: ""), (eq(newHash.value) ?: ""), (any<Instant>() ?: Instant.EPOCH))).thenReturn(1)
        adapter.changePasswordAndRevoke(123L, oldHash, newHash)
        verify(repository).changePasswordAndRevoke(eq(123L), (eq(oldHash.value) ?: ""), (eq(newHash.value) ?: ""), (any<Instant>() ?: Instant.EPOCH))
        verifyNoMoreInteractions(repository)
    }

    @Test
    fun stalePasswordIsRejectedAndDatabaseFailurePropagates() {
        assertEquals(ErrorCode.ACCOUNT_CHANGED, assertThrows(IdentityDomainException::class.java) {
            adapter.changePasswordAndRevoke(123L, oldHash, newHash)
        }.errorCode)
        `when`(repository.changePasswordAndRevoke(eq(123L), (eq(oldHash.value) ?: ""), (eq(newHash.value) ?: ""), (any<Instant>() ?: Instant.EPOCH)))
            .thenThrow(org.springframework.dao.DataAccessResourceFailureException("database unavailable"))
        assertThrows(org.springframework.dao.DataAccessResourceFailureException::class.java) {
            adapter.changePasswordAndRevoke(123L, oldHash, newHash)
        }
    }
}
