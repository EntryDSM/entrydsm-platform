package hs.kr.entrydsm.observability

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ObservabilityBootstrapApplication

fun main(args: Array<String>) {
    runApplication<ObservabilityBootstrapApplication>(*args)
}
