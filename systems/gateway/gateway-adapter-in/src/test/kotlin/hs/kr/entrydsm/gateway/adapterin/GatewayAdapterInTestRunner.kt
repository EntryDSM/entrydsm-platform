package hs.kr.entrydsm.gateway.adapterin

import hs.kr.entrydsm.gateway.adapterin.configuration.GatewayServicePropertiesTest
import hs.kr.entrydsm.gateway.adapterin.configuration.DownstreamClientPolicyTest
import hs.kr.entrydsm.gateway.adapterin.error.DownstreamFailureGlobalFilterTest
import hs.kr.entrydsm.gateway.adapterin.error.GatewayGlobalExceptionHandlerTest
import hs.kr.entrydsm.gateway.adapterin.filter.GatewayCorsGlobalFilterTest
import hs.kr.entrydsm.gateway.adapterin.integration.GatewayProxyIntegrationTest
import hs.kr.entrydsm.gateway.adapterin.trace.TraceIdGlobalFilterTest
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main() {
    val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
        .selectors(
            selectClass(TraceIdGlobalFilterTest::class.java),
            selectClass(GatewayServicePropertiesTest::class.java),
            selectClass(DownstreamClientPolicyTest::class.java),
            selectClass(DownstreamFailureGlobalFilterTest::class.java),
            selectClass(GatewayGlobalExceptionHandlerTest::class.java),
            selectClass(GatewayCorsGlobalFilterTest::class.java),
            selectClass(GatewayProxyIntegrationTest::class.java),
        )
        .build()
    val listener = SummaryGeneratingListener()
    val launcher = LauncherFactory.create()

    launcher.registerTestExecutionListeners(listener)
    launcher.execute(request)

    listener.summary.printTo(PrintWriter(System.out))
    listener.summary.failures.forEach { failure ->
        failure.exception.printStackTrace(System.out)
    }
    if (listener.summary.testsFailedCount > 0) {
        exitProcess(1)
    }
}
