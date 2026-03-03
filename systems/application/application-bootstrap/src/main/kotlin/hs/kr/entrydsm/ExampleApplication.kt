package hs.kr.entrydsm.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ApplicationBootstrapApplication

fun main(args: Array<String>) {
    runApplication<ApplicationBootstrapApplication>(*args)
}
