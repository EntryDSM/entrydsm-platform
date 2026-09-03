package hs.kr.entrydsm.identity.adapterout.pass

import hs.kr.entrydsm.identity.application.port.out.PassProviderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertThrows

class KcbPassProviderAdapterTest {
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
