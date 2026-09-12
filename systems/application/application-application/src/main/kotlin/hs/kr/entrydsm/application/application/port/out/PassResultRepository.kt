package hs.kr.entrydsm.application.application.port.out

import hs.kr.entrydsm.application.domain.model.PassResult

interface PassResultRepository {
    /**
     * 지원자 × 단계 키로 발표 결과를 덮어씁니다.
     *
     * @return 실제로 저장된 건수
     */
    fun upsertAll(results: List<PassResult>): Int
}
