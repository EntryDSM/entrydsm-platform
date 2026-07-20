package hs.kr.entrydsm.identity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IdentityBootstrapApplication

fun main(args: Array<String>) {
    runApplication<IdentityBootstrapApplication>(*args)
}
