package hs.kr.entrydsm.observability.adapterin.web.sse

import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * ponytail: 커넥션 목록을 인메모리로만 들고 있어 다중 인스턴스에서는 인스턴스별로 브로드캐스트가 갈린다.
 * 수평 확장이 필요해지면 Redis Pub/Sub로 교체한다.
 */
@Component
class SseBroadcaster(
    private val getDashboardSnapshotUseCase: GetDashboardSnapshotUseCase,
) {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()
    private val eventIdSeq = AtomicLong(System.currentTimeMillis())

    fun register(emitter: SseEmitter) {
        emitters.add(emitter)
        emitter.onCompletion { emitters.remove(emitter) }
        emitter.onTimeout { emitters.remove(emitter) }
        emitter.onError { emitters.remove(emitter) }
        sendInitialSnapshot(emitter)
    }

    fun publishLog(payload: Any) {
        broadcast("log", payload)
    }

    @Scheduled(fixedRate = 5000)
    fun broadcastFrequent() {
        if (emitters.isEmpty()) return
        val snapshot = getDashboardSnapshotUseCase.getSnapshot(null)
        broadcast("traffic", snapshot.traffic.toResponse())
        broadcast("api", snapshot.api.toResponse())
        broadcast("business", snapshot.business.toResponse())
        broadcast("service", snapshot.services.toResponse())
    }

    @Scheduled(fixedRate = 300000)
    fun broadcastResource() {
        if (emitters.isEmpty()) return
        broadcast("resource", getDashboardSnapshotUseCase.getSnapshot(null).resource.toResponse())
    }

    @Scheduled(fixedRate = 15000)
    fun ping() {
        emitters.forEach { emitter ->
            runCatching { emitter.send(SseEmitter.event().comment("ping")) }
                .onFailure { emitters.remove(emitter) }
        }
    }

    private fun sendInitialSnapshot(emitter: SseEmitter) {
        runCatching {
            emitter.send(SseEmitter.event().reconnectTime(RECONNECT_TIME_MS))
            val snapshot = getDashboardSnapshotUseCase.getSnapshot(null)
            emitter.send(SseEmitter.event().id(nextId()).name("snapshot").data(snapshot.toResponse()))
        }.onFailure { emitters.remove(emitter) }
    }

    private fun broadcast(eventName: String, data: Any) {
        emitters.forEach { emitter ->
            runCatching { emitter.send(SseEmitter.event().id(nextId()).name(eventName).data(data)) }
                .onFailure { emitters.remove(emitter) }
        }
    }

    private fun nextId(): String = eventIdSeq.incrementAndGet().toString()

    companion object {
        private const val RECONNECT_TIME_MS = 5000L
    }
}
