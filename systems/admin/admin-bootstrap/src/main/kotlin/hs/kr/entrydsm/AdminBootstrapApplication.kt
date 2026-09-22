package hs.kr.entrydsm.admin

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AdminBootstrapApplication(@Value("\${admin.storage.environment}") environment: String) {
    init { DocumentNaming.keyRoot(environment) }
}

fun main(args: Array<String>) {
    runApplication<AdminBootstrapApplication>(*args)
}
