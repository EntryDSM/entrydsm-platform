package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.application.port.out.ApplicationDataPort
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.transaction.annotation.Transactional

class TransactionalAccountRegistrationAdapterTest {
    @Test
    fun registrationBoundaryIsTransactional() {
        assertNotNull(
            TransactionalAccountRegistrationAdapter::class.java
                .getDeclaredMethod("register", AccountRegistration::class.java, Instant::class.java)
                .getAnnotation(Transactional::class.java)
        )
    }

    @Test
    fun applicationCreationFailureIsPropagatedForTransactionRollback() {
        val registration = mock(AccountRegistration::class.java)
        val savedAccount = mock(Account::class.java)
        val accountCommandPort = mock(AccountCommandPort::class.java)
        val applicationDataPort = mock(ApplicationDataPort::class.java)
        val failure = IllegalStateException("application creation failed")

        `when`(savedAccount.userId).thenReturn(123L)
        `when`(accountCommandPort.register(registration, CREATED_AT)).thenReturn(savedAccount)
        doThrow(failure).`when`(applicationDataPort).create(123L, CREATED_AT)

        val thrown = try {
            TransactionalAccountRegistrationAdapter(accountCommandPort, applicationDataPort)
                .register(registration, CREATED_AT)
            null
        } catch (exception: IllegalStateException) {
            exception
        }

        assertSame(failure, thrown)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
