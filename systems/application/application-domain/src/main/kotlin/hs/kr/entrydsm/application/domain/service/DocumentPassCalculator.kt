package hs.kr.entrydsm.application.domain.service

import hs.kr.entrydsm.application.domain.enum.AdmissionType
import hs.kr.entrydsm.application.domain.enum.PassResultStatus
import hs.kr.entrydsm.application.domain.model.Applicant

class DocumentPassCalculator {
    fun calculate(applicants: List<Applicant>): Map<Long, PassResultStatus> {
        if (applicants.size <= ALL_PASS_LIMIT) {
            return applicants.associate { it.id to PassResultStatus.PASS }
        }

        val passedIds = applicants
            .filter { it.admissionType != null }
            .groupBy { requireNotNull(it.admissionType) }
            .flatMap { (type, candidates) ->
                candidates
                    .sortedWith(compareByDescending<Applicant> { it.totalScore ?: Double.NEGATIVE_INFINITY }.thenBy { it.id })
                    .take(requireNotNull(DOCUMENT_PASS_LIMITS[type]))
            }
            .mapTo(hashSetOf()) { it.id }

        return applicants.associate { applicant ->
            applicant.id to if (applicant.id in passedIds) PassResultStatus.PASS else PassResultStatus.FAIL
        }
    }

    private companion object {
        const val ALL_PASS_LIMIT = 128
        val DOCUMENT_PASS_LIMITS = mapOf(
            AdmissionType.REGULAR to 64,
            AdmissionType.MEISTER to 32,
            AdmissionType.SOCIAL to 32,
        )
    }
}
