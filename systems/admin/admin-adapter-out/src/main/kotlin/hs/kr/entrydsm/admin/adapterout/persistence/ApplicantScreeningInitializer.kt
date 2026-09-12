package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantScreeningJpaEntity
import hs.kr.entrydsm.admin.adapterout.grpc.ApplicantRecord
import hs.kr.entrydsm.admin.adapterout.repository.ApplicantScreeningJpaRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 처음 보는 원서에 접수 번호를 붙여 전형 정보를 만들어 둡니다.
 *
 * 접수 번호는 한 번 정해지면 수험표와 원서 파일의 이름에 들어가므로 다시 매기면 안 됩니다.
 * 그래서 조회 결과로부터 그때그때 계산하지 않고 여기서 한 번만 부여해 저장합니다.
 * 조회 흐름이 읽기 전용 트랜잭션일 수 있어 별도 트랜잭션으로 씁니다.
 *
 * ponytail: 같은 순간에 목록 조회가 겹치면 접수 번호 부여가 충돌할 수 있다. 관리자
 * 몇 명이 쓰는 화면이라 지금은 실패 후 재시도로 충분하다. 잦아지면 번호 채번을
 * 시퀀스 테이블로 뺀다.
 */
@Component
class ApplicantScreeningInitializer(
    private val screeningRepository: ApplicantScreeningJpaRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun ensureFor(records: List<ApplicantRecord>): Map<Long, ApplicantScreeningJpaEntity> {
        if (records.isEmpty()) return emptyMap()

        val existing = screeningRepository.findAllById(records.map { it.applicantId })
            .associateBy { it.applicantId }
        val missing = records.filterNot { it.applicantId in existing }
        if (missing.isEmpty()) return existing

        var nextReceiptNumber = screeningRepository.findMaxReceiptNumber() + 1
        val now = Instant.now(clock)
        val created = missing
            .sortedWith(compareBy({ it.submittedAt ?: Instant.MAX }, { it.applicantId }))
            .map { record ->
                ApplicantScreeningJpaEntity(
                    applicantId = record.applicantId,
                    receiptNumber = nextReceiptNumber++,
                    updatedAt = now,
                )
            }

        return existing + screeningRepository.saveAll(created).associateBy { it.applicantId }
    }
}
