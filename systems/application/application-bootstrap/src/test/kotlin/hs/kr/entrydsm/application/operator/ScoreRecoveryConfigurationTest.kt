package hs.kr.entrydsm.application.operator

import hs.kr.entrydsm.application.administration.adapterout.recovery.ScoreRecoveryService
import org.junit.Assert.*
import org.junit.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

class ScoreRecoveryConfigurationTest {
    @Test fun offlineContextDoesNotRegisterServersWorkersOrEventRelays() {
        SpringApplicationBuilder(ScoreRecoveryConfiguration::class.java).web(WebApplicationType.NONE)
            .logStartupInfo(false).profiles("test").run().use { context ->
                assertNotNull(context.getBean(ScoreRecoveryService::class.java))
                for (bean in listOf("grpcServer", "configurationGrpcChannel", "exportJobDispatcher",
                    "applicantStatusOutboxRelay", "screeningResultEventHandler", "applicationBootstrapApplication")) {
                    assertFalse(bean, context.containsBean(bean))
                }
            }
    }
}
