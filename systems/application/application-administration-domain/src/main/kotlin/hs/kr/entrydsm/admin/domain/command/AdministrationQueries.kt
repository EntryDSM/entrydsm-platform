package hs.kr.entrydsm.admin.domain.command

import hs.kr.entrydsm.admin.domain.enum.StatisticsMetric
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.PageRequest

/** 이름이 지정된 내부 RPC의 요청 형식. 외부 HTTP 응답에는 추가되지 않는다. */
data class SearchApplicantsQuery(val filter: ApplicantFilter, val page: PageRequest)
data class ApplicantIdQuery(val applicantId: Long)
data class ExportIdQuery(val exportJobId: String)
data class StatisticsQuery(val metrics: Set<StatisticsMetric>)
