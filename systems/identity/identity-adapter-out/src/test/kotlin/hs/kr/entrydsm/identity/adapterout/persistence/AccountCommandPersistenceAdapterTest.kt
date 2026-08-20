package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountAlreadyExistsException
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.AccountRepository
import java.time.Instant
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock

class AccountCommandPersistenceAdapterTest {
    @Test
    fun duplicateRegistrationIsMappedToAccountAlreadyExists() {
        val repository = mock(AccountRepository::class.java)
        val registration = mock(AccountRegistration::class.java)
        val failure = org.springframework.dao.DataIntegrityViolationException("duplicate login id")
        doThrow(failure).`when`(repository).register(registration, CREATED_AT)

        val thrown = try {
            AccountCommandPersistenceAdapter(repository).register(registration, CREATED_AT)
            null
        } catch (exception: AccountAlreadyExistsException) {
            exception
        }

        assertSame(failure, thrown?.cause)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
