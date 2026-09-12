package hs.kr.entrydsm.admin.domain.document

/**
 * 관리자가 발급하는 문서의 저장소 객체 키 규칙입니다.
 *
 * configuration 시스템의 `FileNaming`/`FileCategory`와 **같은 버킷의 같은 키**를 가리켜야
 * 합니다. 수험생이 올린 원서 원본은 configuration 이 저장하고 admin 이 내려주므로,
 * 접두사 하나만 어긋나도 admin 의 원서 원본 다운로드가 계속 404 가 됩니다.
 *
 * 접수 번호가 configuration 의 접수 코드입니다.
 *
 * ponytail: 두 시스템이 같은 규칙을 각자 들고 있다. 세 번째 시스템이 같은 걸 필요로 하면
 * packages/ 공용 모듈로 올린다. 그전까지는 한쪽을 고치면 반드시 다른 쪽도 같이 고친다.
 */
object DocumentNaming {

    /** configuration 의 `FileCategory.KEY_ROOT` 와 같아야 합니다. */
    const val KEY_ROOT = "dsm_Entry/Backend/"

    fun admissionTicketObjectKey(receiptNumber: Int): String =
        "${KEY_ROOT}admission-ticket/admission_ticket_$receiptNumber.pdf"

    fun applicationDocumentObjectKey(receiptNumber: Int): String =
        "${KEY_ROOT}application/application_$receiptNumber.pdf"

    fun applicantListObjectKey(exportJobId: String): String =
        "${KEY_ROOT}applicant-list/applicants_$exportJobId.csv"

    fun admissionTicketBundleObjectKey(exportJobId: String): String =
        "${KEY_ROOT}admission-ticket/admission_tickets_$exportJobId.zip"
}
