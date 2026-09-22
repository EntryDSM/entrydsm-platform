package hs.kr.entrydsm.configuration.domain.document.port.out

import hs.kr.entrydsm.configuration.domain.document.ApplicationForm

/** 요강 서식 원본에 원서 값을 찍어 PDF 로 만든다. 서식 원본과 칸 위치는 어댑터가 가진다. */
interface ApplicationFormPdfPort {

    /** @param photo 원서 주인이 올린 증명사진 원본. 없거나 읽지 못하는 형식이면 사진 칸을 비운다. */
    fun render(form: ApplicationForm, photo: ByteArray?): ByteArray

    fun renderEssay(form: ApplicationForm, introduction: Boolean): ByteArray =
        throw UnsupportedOperationException()
}
