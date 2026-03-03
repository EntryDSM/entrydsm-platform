package hs.kr.entrydsm.configuration

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConfigurationBootstrapApplication

fun main(args: Array<String>) {
    runApplication<ConfigurationBootstrapApplication>(*args)
}
