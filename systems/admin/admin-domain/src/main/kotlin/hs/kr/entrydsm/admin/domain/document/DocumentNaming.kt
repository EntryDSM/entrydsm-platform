package hs.kr.entrydsm.admin.domain.document

/**
 * 관리자가 발급하는 문서의 저장소 객체 키 규칙입니다.
 *
 * configuration 시스템의 `FileNaming`/`FileCategory`와 같은 규칙을 따릅니다.
 *
 * ponytail: 두 시스템이 같은 규칙을 각자 들고 있다. 세 번째 시스템이 같은 걸 필요로 하면
 * packages/ 공용 모듈로 올린다.
 */
object DocumentNaming {

    fun admissionTicketObjectKey(receiptNumber: Int): String =
        "admission-ticket/admission_ticket_$receiptNumber.pdf"

    fun applicationDocumentObjectKey(receiptNumber: Int): String =
        "application/application_$receiptNumber.pdf"

    fun applicantListObjectKey(exportJobId: String): String =
        "applicant-list/applicants_$exportJobId.csv"

    fun admissionTicketBundleObjectKey(exportJobId: String): String =
        "admission-ticket/admission_tickets_$exportJobId.zip"
}
