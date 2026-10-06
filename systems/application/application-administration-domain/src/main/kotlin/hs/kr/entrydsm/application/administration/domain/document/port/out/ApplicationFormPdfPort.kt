package hs.kr.entrydsm.application.administration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.*

import hs.kr.entrydsm.application.administration.domain.document.ApplicationForm

/** 요강 서식 원본에 원서 값을 찍어 PDF 로 만든다. 서식 원본과 칸 위치는 어댑터가 가진다. */
interface ApplicationFormPdfPort {

    /** @param photo 원서 주인이 올린 증명사진 원본. 없거나 읽지 못하는 형식이면 사진 칸을 비운다. */
    fun render(form: ApplicationForm, photo: ByteArray?): ByteArray

    fun renderEssay(form: ApplicationForm, introduction: Boolean): ByteArray =
        throw UnsupportedOperationException()

    /** 최종 합격자 등록 서류 원본([template]) 첫 장 입학 동의서에 지원자 정보를 찍는다. 나머지 장은 그대로다. */
    fun renderRegistrationDocument(form: ApplicationForm, template: ByteArray): ByteArray =
        throw UnsupportedOperationException()

    /** [renderRegistrationDocument] 가 채울 수 있는 원본인지. PDF 로 열리고 첫 장이 있어야 한다. */
    fun isRegistrationTemplate(template: ByteArray): Boolean
}
