package hs.kr.entrydsm.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class IdentityBootstrapApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<IdentityBootstrapApplication>(*args)
}
