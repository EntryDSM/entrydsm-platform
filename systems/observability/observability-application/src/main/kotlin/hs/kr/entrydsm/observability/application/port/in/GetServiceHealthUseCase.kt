package hs.kr.entrydsm.observability.application.port.`in`

import hs.kr.entrydsm.observability.application.port.`in`.result.ServiceHealthResult

interface GetServiceHealthUseCase {
    fun getHealth(): ServiceHealthResult
}
