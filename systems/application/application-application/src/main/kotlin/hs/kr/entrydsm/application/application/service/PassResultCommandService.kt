package hs.kr.entrydsm.application.application.service

import hs.kr.entrydsm.application.application.exception.ApplicantNotFoundException
import hs.kr.entrydsm.application.application.port.`in`.PassResultPort
import hs.kr.entrydsm.application.application.port.`in`.command.AnnouncePassResultsCommand
import hs.kr.entrydsm.application.application.port.out.ApplicantRepository
import hs.kr.entrydsm.application.application.port.out.PassResultRepository

/**
 * 전형 결과 발표를 받아 저장합니다.
 *
 * 산출은 admin 이 하지만 수험생에게 보이는 합격 여부의 소유자는 application 입니다.
 * 한 번의 발표를 통째로 받아 전부 반영하거나 전부 거부합니다. 일부만 반영되면
 * 어떤 지원자가 발표되었는지 admin 과 application 의 판단이 갈리기 때문입니다.
 */
class PassResultCommandService(
    private val applicantRepository: ApplicantRepository,
    private val passResultRepository: PassResultRepository,
) : PassResultPort {
    override fun announce(command: AnnouncePassResultsCommand): Int {
        if (command.results.isEmpty()) return 0

        val duplicated = command.results
            .groupingBy { it.applicantId to it.resultType }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicated.isEmpty()) {
            "duplicate pass result entries: ${duplicated.joinToString { "${it.first}/${it.second}" }}"
        }

        val requestedIds = command.results.mapTo(mutableSetOf()) { it.applicantId }
        val missing = requestedIds - applicantRepository.existingIds(requestedIds)
        if (missing.isNotEmpty()) {
            throw ApplicantNotFoundException(missing.min())
        }

        return passResultRepository.upsertAll(command.results)
    }
}
