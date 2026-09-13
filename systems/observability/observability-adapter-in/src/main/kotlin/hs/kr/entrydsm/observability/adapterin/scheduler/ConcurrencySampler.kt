package hs.kr.entrydsm.observability.adapterin.scheduler

import hs.kr.entrydsm.observability.application.port.`in`.SampleConcurrencyUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 동시접속 최대·평균은 표본이 쌓여야 나온다.
 * 인스턴스마다 돌지만 5초 구간마다 한 인스턴스의 표본만 기록된다(RedisSessionStoreAdapter).
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
