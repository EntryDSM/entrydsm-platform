package hs.kr.entrydsm.application.administration.domain.document

import hs.kr.entrydsm.configuration.domain.document.FileExtension

object DocumentFileNaming {
    /** 원서·수험표 파일명의 번호는 서식에 찍는 접수번호와 같은 표기다. */
    fun applicationFileName(applicantId: Long): String =
        "application_${ReceiptNumber.of(applicantId)}.${FileExtension.PDF.value}"

    fun admissionTicketFileName(applicantId: Long): String =
        "admission_ticket_${ReceiptNumber.of(applicantId)}.${FileExtension.PDF.value}"

    fun registrationFormFileName(applicantId: Long): String =
        "registration_form_${ReceiptNumber.of(applicantId)}.${FileExtension.PDF.value}"

}
