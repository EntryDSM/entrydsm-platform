package hs.kr.entrydsm.observability.adapterout.round

import hs.kr.entrydsm.observability.application.port.out.Round
import hs.kr.entrydsm.observability.application.port.out.RoundPort
import org.springframework.stereotype.Component

@Component
class StaticRoundAdapter(
    private val properties: MonitorRoundProperties,
) : RoundPort {
    override fun current(): Round = Round(properties.name, properties.from, properties.to)
}
