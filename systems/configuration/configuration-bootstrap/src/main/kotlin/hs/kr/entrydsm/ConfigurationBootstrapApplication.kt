package hs.kr.entrydsm.configuration

import hs.kr.entrydsm.configuration.domain.document.FileCategory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConfigurationBootstrapApplication(@Value("\${aws.s3.environment}") environment: String) {
    init { FileCategory.keyRoot(environment) }
}

fun main(args: Array<String>) {
    runApplication<ConfigurationBootstrapApplication>(*args)
}
