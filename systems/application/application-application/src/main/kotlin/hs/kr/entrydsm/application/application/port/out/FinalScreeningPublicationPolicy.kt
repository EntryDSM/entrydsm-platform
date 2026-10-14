package hs.kr.entrydsm.application.application.port.out

fun interface FinalScreeningPublicationPolicy {
    /** 최종 합격 발표 시각과 발표 처리 완료 여부를 함께 확인한다. */
    fun isPublished(): Boolean
}
