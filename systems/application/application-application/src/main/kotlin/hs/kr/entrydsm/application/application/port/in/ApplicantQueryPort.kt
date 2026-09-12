package hs.kr.entrydsm.application.application.port.`in`

import hs.kr.entrydsm.application.application.port.`in`.result.ApplicantSummaryResult

interface ApplicantQueryPort {
    /** 제출된 원서 전체를 접수 순서(제출 시각)대로 돌려줍니다. */
    fun findAllSubmitted(): List<ApplicantSummaryResult>
}
