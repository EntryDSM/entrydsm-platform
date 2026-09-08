package hs.kr.entrydsm.identity.adapterout.pass

import hs.kr.entrydsm.identity.application.port.out.PassProviderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class KcbPassProviderAdapterTest {
    @Test
    fun parsesVerifiedBirthday() {
        assertEquals(LocalDate.of(2009, 3, 15), parseKcbBirthdate("20090315"))
    }

    @Test
    fun rejectsMissingOrInvalidBirthday() {
        listOf("", "20090229", "2009-03-15").forEach { value ->
            assertThrows(PassProviderException::class.java) { parseKcbBirthdate(value) }
        }
    }

    @Test
    fun successfulResponseRequiresModelToken() {
        assertThrows(PassProviderException::class.java) {
            popupModelToken("B000", " ")
        }
    }

    @Test
    fun onlySuccessfulResponseCarriesModelTokenToPopup() {
        assertEquals("model-token", popupModelToken("B000", "model-token"))
        assertNull(popupModelToken("C000", "model-token"))
    }
}
