package hs.kr.entrydsm.observability.adapterin.scheduler

import hs.kr.entrydsm.observability.application.port.`in`.SampleConcurrencyUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 동시접속 최대·평균은 표본이 쌓여야 나온다.
 * 인스턴스마다 돌면 표본 수만 인스턴스 수만큼 늘 뿐, 모두 같은 Redis 값을 재므로 최대·평균은 달라지지 않는다.
 */
@Component
class ConcurrencySampler(
    private val sampleConcurrencyUseCase: SampleConcurrencyUseCase,
) {
    @Scheduled(fixedRate = 5000)
    fun sample() {
        sampleConcurrencyUseCase.sampleConcurrency()
    }
}
