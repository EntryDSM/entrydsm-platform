package hs.kr.entrydsm.observability.application.port.`in`

fun interface SampleConcurrencyUseCase {
    /** 지금의 동시접속자 수를 최대·평균 집계용 표본으로 남긴다. */
    fun sampleConcurrency()
}
