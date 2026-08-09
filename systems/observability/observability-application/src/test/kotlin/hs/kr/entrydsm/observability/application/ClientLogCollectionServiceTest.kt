package hs.kr.entrydsm.observability.application

import hs.kr.entrydsm.observability.application.port.`in`.ClientLogItem
import hs.kr.entrydsm.observability.application.port.out.ClientLogEntry
import hs.kr.entrydsm.observability.application.port.out.ClientLogInput
import hs.kr.entrydsm.observability.application.port.out.ClientLogPage
import hs.kr.entrydsm.observability.application.port.out.ClientLogStorePort
import hs.kr.entrydsm.observability.application.port.out.RateLimitPort
import hs.kr.entrydsm.observability.application.port.out.LiveLogPublisherPort
import hs.kr.entrydsm.observability.domain.enum.LogLevel
import hs.kr.entrydsm.observability.domain.enum.LogSource
import hs.kr.entrydsm.observability.domain.exception.MonitorDomainException
import hs.kr.entrydsm.observability.domain.model.Cursor
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ClientLogCollectionServiceTest {
    private val store = FakeClientLogStorePort()

    private fun item() = ClientLogItem(
        level = LogLevel.ERROR,
        source = LogSource.DOM,
        message = "a".repeat(600),
        stack = null,
        pageUrl = "/application/personal",
        occurredAt = Instant.parse("2026-07-28T13:58:41Z"),
    )

    @Test
    fun truncatesOverlongMessageAndRecordsEachItem() {
        val service = ClientLogCollectionService(store, FakeRateLimitPort(true), LiveLogPublisherPort {})

        val result = service.record("sess_1", listOf(item()), "Mozilla/5.0 (Windows NT 10.0) Chrome/138.0.0.0", "127.0.0.1")

        assertEquals(1, result.accepted)
        assertEquals(0, result.rejected)
        assertEquals(500, store.recorded.single().message.length)
        assertEquals("Chrome 138", store.recorded.single().browser)
    }

    @Test
    fun rejectsEmptyOrOversizedBatch() {
        val service = ClientLogCollectionService(store, FakeRateLimitPort(true), LiveLogPublisherPort {})

        assertThrows(MonitorDomainException::class.java) { service.record("sess_1", emptyList(), null, "127.0.0.1") }
        assertThrows(MonitorDomainException::class.java) {
            service.record("sess_1", List(21) { item() }, null, "127.0.0.1")
        }
    }

    @Test
    fun rateLimitExceededThrowsTooManyRequests() {
        val service = ClientLogCollectionService(store, FakeRateLimitPort(false), LiveLogPublisherPort {})

        assertThrows(MonitorDomainException::class.java) {
            service.record("sess_1", listOf(item()), null, "127.0.0.1")
        }
    }

    private class FakeClientLogStorePort : ClientLogStorePort {
        val recorded = mutableListOf<ClientLogInput>()
        override fun record(input: ClientLogInput) {
            recorded.add(input)
        }
        override fun countByLevel(from: Instant, to: Instant): Map<LogLevel, Long> = emptyMap()
        override fun list(from: Instant, to: Instant, levels: Set<LogLevel>?, cursor: Cursor?, size: Int): ClientLogPage =
            ClientLogPage(0, 0, 0, emptyList<ClientLogEntry>(), null, false)
    }

    private class FakeRateLimitPort(private val allow: Boolean) : RateLimitPort {
        override fun tryAcquire(key: String, limit: Long, windowSeconds: Long) = allow
    }
}
