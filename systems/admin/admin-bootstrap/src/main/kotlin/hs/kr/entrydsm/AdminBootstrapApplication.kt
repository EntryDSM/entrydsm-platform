package hs.kr.entrydsm.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AdminBootstrapApplication

fun main(args: Array<String>) {
    runApplication<AdminBootstrapApplication>(*args)
}
