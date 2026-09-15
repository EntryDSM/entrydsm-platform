package hs.kr.entrydsm.application

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ApplicationBootstrapApplication

fun main(args: Array<String>) {
    runApplication<ApplicationBootstrapApplication>(*args)
}
