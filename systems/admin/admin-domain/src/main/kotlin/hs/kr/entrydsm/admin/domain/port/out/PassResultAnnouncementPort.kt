package hs.kr.entrydsm.admin.domain.port.out

import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import java.time.Instant

/**
 * 전형 결과는 admin 이 산출하지만 수험생에게 보이는 합격 여부의 소유자는 application 입니다.
 * admin 은 산출 결과를 넘기기만 합니다.
 */
interface PassResultAnnouncementPort {
    /**
     * 산출된 결과를 한 번에 넘깁니다.
     *
     * @return 원본에 반영된 건수
     */
    fun announce(results: List<AnnouncedPassResult>, processedAt: Instant): Int
}

/**
 * @property applicantId application 시스템의 지원자 식별자
 * @property status 산출된 전형 상태. 1차/최종과 합격/불합격이 여기서 갈린다
 */
data class AnnouncedPassResult(
    val applicantId: Long,
    val status: ApplicantStatus,
)
