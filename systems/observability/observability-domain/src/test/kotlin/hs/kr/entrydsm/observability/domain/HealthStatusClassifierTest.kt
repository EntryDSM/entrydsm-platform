package hs.kr.entrydsm.observability.domain

import hs.kr.entrydsm.observability.domain.enum.ServiceStatus
import hs.kr.entrydsm.observability.domain.service.HealthStatusClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthStatusClassifierTest {
    @Test
    fun classifiesUpWhenFastAndHealthy() {
        assertEquals(
            ServiceStatus.UP,
            HealthStatusClassifier.classify(responseTimeMs = 12, allDependenciesUp = true),
        )
    }

    @Test
    fun classifiesDegradedWhenSlow() {
        assertEquals(
            ServiceStatus.DEGRADED,
            HealthStatusClassifier.classify(responseTimeMs = 1840, allDependenciesUp = true),
        )
    }

    @Test
    fun classifiesDegradedWhenDependencyDown() {
        assertEquals(
            ServiceStatus.DEGRADED,
            HealthStatusClassifier.classify(responseTimeMs = 12, allDependenciesUp = false),
        )
    }

    @Test
    fun classifiesDownWhenUnreachable() {
        assertEquals(
            ServiceStatus.DOWN,
            HealthStatusClassifier.classify(responseTimeMs = null, allDependenciesUp = true),
        )
    }

    @Test
    fun overallFollowsWorstStatus() {
        assertEquals(
            ServiceStatus.DOWN,
            HealthStatusClassifier.overall(listOf(ServiceStatus.UP, ServiceStatus.DEGRADED, ServiceStatus.DOWN)),
        )
        assertEquals(
            ServiceStatus.DEGRADED,
            HealthStatusClassifier.overall(listOf(ServiceStatus.UP, ServiceStatus.DEGRADED)),
        )
        assertEquals(
            ServiceStatus.UP,
            HealthStatusClassifier.overall(listOf(ServiceStatus.UP, ServiceStatus.UP)),
        )
    }
}
