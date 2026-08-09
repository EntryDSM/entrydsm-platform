package hs.kr.entrydsm.observability.adapterout.round

import java.time.Instant
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** 진행 중인 접수 회차를 관측 서비스 자체는 알지 못하므로 배포 설정으로 지정한다. */
@Component
@ConfigurationProperties(prefix = "monitor.round")
class MonitorRoundProperties {
    var name: String = "current"
    var from: Instant = Instant.EPOCH
    var to: Instant = Instant.EPOCH
}
