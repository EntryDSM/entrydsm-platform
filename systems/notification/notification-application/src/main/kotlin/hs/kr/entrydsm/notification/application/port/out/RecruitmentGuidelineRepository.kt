package hs.kr.entrydsm.notification.application.port.out

import hs.kr.entrydsm.notification.domain.model.RecruitmentGuideline

interface RecruitmentGuidelineRepository {
    fun findCurrent(): RecruitmentGuideline?
}

