package hs.kr.entrydsm.admission

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AdmissionBootstrapApplication

fun main(args: Array<String>) {
    runApplication<AdmissionBootstrapApplication>(*args)
}
