package hs.kr.entrydsm.observability

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class ObservabilityBootstrapApplication

fun main(args: Array<String>) {
    runApplication<ObservabilityBootstrapApplication>(*args)
}
