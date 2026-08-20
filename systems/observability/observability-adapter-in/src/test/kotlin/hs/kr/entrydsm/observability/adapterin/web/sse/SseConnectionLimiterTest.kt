package hs.kr.entrydsm.observability.adapterin.web.sse

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SseConnectionLimiterTest {

    @Test
    fun rejectsConnectionsOverTheLimit() {
        val limiter = SseConnectionLimiter()

        repeat(MAX_CONNECTIONS) { assertTrue(limiter.tryAcquire("1.2.3.4")) }

        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun releasedSlotCanBeAcquiredAgain() {
        val limiter = SseConnectionLimiter()
        repeat(MAX_CONNECTIONS) { limiter.tryAcquire("1.2.3.4") }

        limiter.release("1.2.3.4")

        assertTrue(limiter.tryAcquire("1.2.3.4"))
        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun releaseBeyondZeroDoesNotCreateExtraSlots() {
        val limiter = SseConnectionLimiter()

        repeat(MAX_CONNECTIONS + 2) { limiter.release("1.2.3.4") }

        repeat(MAX_CONNECTIONS) { assertTrue(limiter.tryAcquire("1.2.3.4")) }
        assertFalse(limiter.tryAcquire("1.2.3.4"))
    }

    @Test
    fun concurrentAcquiresNeverExceedTheLimit() {
        val limiter = SseConnectionLimiter()
        val acquired = AtomicInteger(0)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREADS)
        val pool = Executors.newFixedThreadPool(THREADS)
        repeat(THREADS) {
            pool.execute {
                start.await()
                if (limiter.tryAcquire("1.2.3.4")) acquired.incrementAndGet()
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(MAX_CONNECTIONS, acquired.get())
    }

    companion object {
        private const val MAX_CONNECTIONS = 3
        private const val THREADS = 16
    }
}
