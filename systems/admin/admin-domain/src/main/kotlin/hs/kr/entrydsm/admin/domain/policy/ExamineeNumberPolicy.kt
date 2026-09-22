package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant

private const val MAX_SEQUENCE = 999

object ExamineeNumberPolicy {
    fun isValidExistingNumber(applicant: Applicant): Boolean =
        applicant.examineeNumber != null && applicant.admissionType != null && applicant.region != null &&
            applicant.validSequence() != null

    fun issue(
        applicants: List<Applicant>,
        distances: Map<Long, Long>,
    ): ExamineeNumberIssuance {
        val targets = applicants.filter { it.isEligible() }
        val (alreadyIssued, pending) = targets.partition { it.examineeNumber != null }
        val reserved = alreadyIssued
            .mapNotNull { applicant -> applicant.validSequence()?.let { applicant.group() to it } }
            .groupBy({ it.first }, { it.second })

        val issued = pending.groupBy { it.group() }.flatMap { (group, groupApplicants) ->
            var sequence = reserved[group].orEmpty().maxOrNull()?.plus(1) ?: 1
            groupApplicants
                .sortedWith(compareBy<Applicant> { distances.getValue(it.id) }.thenBy { it.id })
                .map { applicant ->
                    if (sequence > MAX_SEQUENCE) {
                        throw AdminDomainException(ErrorCode.EXAMINEE_NUMBER_LIMIT_EXCEEDED)
                    }
                    applicant.copy(examineeNumber = "${group.typeCode}${group.regionCode}${sequence++.toString().padStart(3, '0')}")
                }
        }

        return ExamineeNumberIssuance(
            issued = issued,
            skippedCount = alreadyIssued.size,
            totalTargets = targets.size,
        )
    }

    private fun Applicant.isEligible() =
        isArrived && admissionType != null && region != null && !address.isNullOrBlank()

    private fun Applicant.group() = Group(admissionType!!.code, region!!.code)

    private fun Applicant.validSequence(): Int? {
        val number = examineeNumber ?: return null
        val group = group()
        if (!number.matches(Regex("[123][12]\\d{3}")) || number.take(2) != "${group.typeCode}${group.regionCode}") {
            return null
        }
        return number.takeLast(3).toInt().takeIf { it in 1..MAX_SEQUENCE }
    }

    private val AdmissionType.code: Int
        get() = when (this) {
            AdmissionType.MEISTER -> 1
            AdmissionType.SOCIAL -> 2
            AdmissionType.GENERAL -> 3
        }

    private val Region.code: Int
        get() = when (this) {
            Region.DAEJEON -> 1
            Region.NATIONWIDE -> 2
        }

    private data class Group(val typeCode: Int, val regionCode: Int)
}

data class ExamineeNumberIssuance(
    val issued: List<Applicant>,
    val skippedCount: Int,
    val totalTargets: Int,
)
