package hs.kr.entrydsm.admin.domain.policy

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ErrorCode
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant

private const val MAX_SEQUENCE = 999

object ExamineeNumberPolicy {
    fun isValidExistingNumber(applicant: Applicant): Boolean =
        applicant.reservedNumber() != null

    fun issue(
        applicants: List<Applicant>,
        distances: Map<Long, Long>,
    ): ExamineeNumberIssuance {
        val targets = applicants.filter { it.isEligible() }
        val (alreadyIssued, pending) = targets.partition { it.examineeNumber != null }
        // 관리자 정정 후에도 기존 번호는 유지한다. 현재 전형·지역이 아닌 발급 번호의 접두사로 예약한다.
        // 도착 취소·필수값 누락으로 발급 대상에서 빠진 원서의 번호도 재사용하지 않는다.
        val reserved = applicants
            .mapNotNull { applicant -> applicant.reservedNumber() }
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

    private fun Applicant.reservedNumber(): Pair<Group, Int>? {
        val number = examineeNumber ?: return null
        if (!number.matches(Regex("[123][12]\\d{3}"))) return null
        val sequence = number.takeLast(3).toInt().takeIf { it in 1..MAX_SEQUENCE } ?: return null
        return Group(number[0].digitToInt(), number[1].digitToInt()) to sequence
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
