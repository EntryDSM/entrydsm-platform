package hs.kr.entrydsm.admin.application

import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 전형별 모집 정원입니다. 경쟁률을 낼 때 분모로 씁니다.
 */
@ConfigurationProperties(prefix = "admin.quota")
data class AdmissionQuotaProperties(
    val byType: Map<AdmissionType, Int> = emptyMap(),
)
