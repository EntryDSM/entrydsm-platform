package hs.kr.entrydsm.application.application.port.`in`.command

import hs.kr.entrydsm.application.domain.model.PassResult

data class AnnouncePassResultsCommand(
    val results: List<PassResult>,
)
