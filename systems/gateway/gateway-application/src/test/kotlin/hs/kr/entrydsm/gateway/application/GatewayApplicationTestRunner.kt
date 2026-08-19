package hs.kr.entrydsm.gateway.application

import org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main() {
    val request = LauncherDiscoveryRequestBuilder.request()
        .selectors(selectPackage("hs.kr.entrydsm.gateway.application"))
        .build()
    val listener = SummaryGeneratingListener()
    val launcher = LauncherFactory.create()

    launcher.registerTestExecutionListeners(listener)
    launcher.execute(request)
    listener.summary.printTo(PrintWriter(System.out))

    if (listener.summary.testsFailedCount > 0 || listener.summary.containersFailedCount > 0) {
        exitProcess(1)
    }
}
