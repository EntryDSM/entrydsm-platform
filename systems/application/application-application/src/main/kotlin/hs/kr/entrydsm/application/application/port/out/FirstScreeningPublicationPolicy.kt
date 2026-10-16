package hs.kr.entrydsm.application.application.port.out

fun interface FirstScreeningPublicationPolicy {
    /** 발표 시각과 발표 처리 완료 여부를 함께 확인한다. */
    fun isPublished(): Boolean
}
