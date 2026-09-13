package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.AccountCommandPort
import hs.kr.entrydsm.identity.application.port.out.AccountRegistration
import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
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
    fun registrationDoesNotCallApplicationService() {
        val registration = mock(AccountRegistration::class.java)
        val savedAccount = mock(Account::class.java)
        val accountCommandPort = mock(AccountCommandPort::class.java)
        `when`(accountCommandPort.register(registration, CREATED_AT)).thenReturn(savedAccount)

        assertSame(
            savedAccount,
            TransactionalAccountRegistrationAdapter(accountCommandPort).register(registration, CREATED_AT),
        )
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-06-11T10:00:00Z")
    }
}
