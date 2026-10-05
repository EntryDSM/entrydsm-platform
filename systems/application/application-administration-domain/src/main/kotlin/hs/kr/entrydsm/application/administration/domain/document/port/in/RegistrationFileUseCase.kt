package hs.kr.entrydsm.application.administration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester

fun interface RegistrationFileUseCase {
    fun findRegistrationDocument(requester: Requester): DownloadableFile
}
