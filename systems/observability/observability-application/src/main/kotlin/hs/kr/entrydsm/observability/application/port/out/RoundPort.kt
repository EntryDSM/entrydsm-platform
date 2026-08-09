package hs.kr.entrydsm.observability.application.port.out

import java.time.Instant

fun interface RoundPort {
    fun current(): Round
}

data class Round(val name: String, val from: Instant, val to: Instant)
