package hs.kr.entrydsm.gateway.adapterin.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod

class ObservabilityGlobalFilterTest {
    @Test
    fun classifiesBusinessRequests() {
        assertEquals("application-submit", businessMetric(HttpMethod.PATCH, "/api/application/v11/applicants"))
        assertEquals("pdf-download", businessMetric(HttpMethod.GET, "/api/document/v11/applications/12"))
        assertNull(businessMetric(HttpMethod.GET, "/api/application/v11/applicants"))
    }

    @Test
    fun mapsGatewayPathsToObservedServices() {
        assertEquals("APPLICATION", observedService("/api/application/v11/applicants"))
        assertEquals("DOCUMENT", observedService("/api/document/v11/applications"))
        assertNull(observedService("/api/v11/admin/users"))
    }
}
