package hs.kr.entrydsm.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GatewayBootstrapApplication

fun main(args: Array<String>) {
    runApplication<GatewayBootstrapApplication>(*args)
}
