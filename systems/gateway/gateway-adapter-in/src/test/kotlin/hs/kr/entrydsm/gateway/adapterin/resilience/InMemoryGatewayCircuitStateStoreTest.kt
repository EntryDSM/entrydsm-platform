package hs.kr.entrydsm.gateway.adapterin.resilience

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayRuntimeProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InMemoryGatewayCircuitStateStoreTest {
    @Test
    fun transitionsFromOpenToHalfOpenAndClosesAfterSuccessfulProbe() {
        var now = 0L
        val store = InMemoryGatewayCircuitStateStore(nowMillis = { now })
        val policy = policy()

        store.record("identity", failed = true, halfOpen = false, policy).block()
        assertFalse(store.tryAcquire("identity", policy).block()!!.allowed)

        now = 1_001L
        val permit = store.tryAcquire("identity", policy).block()!!
        assertTrue(permit.allowed)
        assertTrue(permit.halfOpen)

        store.record("identity", failed = false, halfOpen = true, policy, permit.permitId).block()
        assertTrue(store.tryAcquire("identity", policy).block()!!.allowed)
    }

    @Test
    fun expiresStaleHalfOpenPermitWithoutReleasingANewPermit() {
        var now = 1_001L
        val store = InMemoryGatewayCircuitStateStore(
            nowMillis = { now },
            probeTimeoutMillis = 100,
        )
        val policy = policy()
        store.record("identity", failed = true, halfOpen = false, policy).block()

        now = 2_002L
        val stalePermit = store.tryAcquire("identity", policy).block()!!
        now = 2_103L
        val currentPermit = store.tryAcquire("identity", policy).block()!!
        assertTrue(currentPermit.allowed)
        assertTrue(currentPermit.halfOpen)
        assertFalse(stalePermit.permitId == currentPermit.permitId)

        store.record("identity", failed = true, halfOpen = true, policy, stalePermit.permitId).block()
        assertFalse(store.tryAcquire("identity", policy).block()!!.allowed)
    }

    @Test
    fun limitsConcurrentHalfOpenPermits() {
        var now = 1_001L
        val store = InMemoryGatewayCircuitStateStore(nowMillis = { now })
        val policy = policy(permittedNumberOfCallsInHalfOpenState = 2)
        store.record("identity", failed = true, halfOpen = false, policy).block()
        now = 2_002L

        assertTrue(store.tryAcquire("identity", policy).block()!!.allowed)
        assertTrue(store.tryAcquire("identity", policy).block()!!.allowed)
        assertFalse(store.tryAcquire("identity", policy).block()!!.allowed)
    }

    @Test
    fun opensWhenSlidingWindowFailureRateReachesThreshold() {
        val store = InMemoryGatewayCircuitStateStore(nowMillis = { 0L })
        val policy = GatewayRuntimeProperties.Resilience(
            failureRateThreshold = 50.0,
            slidingWindowSize = 3,
            minimumNumberOfCalls = 3,
            waitDurationSeconds = 1,
            permittedNumberOfCallsInHalfOpenState = 1,
            stateStore = "memory",
        )

        store.record("identity", failed = false, halfOpen = false, policy).block()
        store.record("identity", failed = false, halfOpen = false, policy).block()
        store.record("identity", failed = true, halfOpen = false, policy).block()
        assertTrue(store.tryAcquire("identity", policy).block()!!.allowed)

        store.record("identity", failed = true, halfOpen = false, policy).block()
        assertFalse(store.tryAcquire("identity", policy).block()!!.allowed)
    }

    private fun policy(
        permittedNumberOfCallsInHalfOpenState: Int = 1,
    ) = GatewayRuntimeProperties.Resilience(
        failureRateThreshold = 100.0,
        slidingWindowSize = 1,
        minimumNumberOfCalls = 1,
        waitDurationSeconds = 1,
        permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState,
        stateStore = "memory",
    )
}
