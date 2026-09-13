package hs.kr.entrydsm.observability.adapterin.web.sse

import hs.kr.entrydsm.observability.adapterin.web.dto.common.toResponse
import hs.kr.entrydsm.observability.application.port.`in`.GetDashboardSnapshotUseCase
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * 구독자 목록은 인스턴스마다 메모리에 있고, 각 인스턴스는 자기에게 붙은 구독자에게만 보낸다.
 * 주기 이벤트는 공용 Redis에서 계산해 어느 인스턴스든 같고, 로그 이벤트는 Redis Pub/Sub으로 모든 인스턴스가 받는다(SseLiveLogPublisher).
 * ponytail: 구독자에게 순차 전송한다. 느린 구독자 하나가 나머지 전송을 지연시킬 수 있다(관리자 대시보드라 동시 구독자가 적다).
 * 구독자가 늘면 커넥션별 bounded 큐와 전용 executor로 분리한다.
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
