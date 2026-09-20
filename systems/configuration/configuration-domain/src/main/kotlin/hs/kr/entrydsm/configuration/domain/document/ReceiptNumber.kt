package hs.kr.entrydsm.configuration.domain.document

/**
 * 접수번호 표기 규칙입니다.
 *
 * 접수 순서대로 매겨진 지원자 번호(`applicantId`)를 네 자리로 채웁니다. 서식 1 의 접수번호 칸과
 * 원서·수험표 파일명이 같은 표기를 씁니다. 9999 번을 넘으면 자릿수가 늘어납니다.
 */
object ReceiptNumber {

    fun of(applicantId: Long): String = "%04d".format(applicantId)
}
