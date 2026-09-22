package hs.kr.entrydsm.configuration.domain.document

private const val SCHOOL_NAME = "대덕소프트웨어마이스터고등학교"
private const val UNISSUED_EXAMINEE_NUMBER = "미발급"

/**
 * 수험표 한 장에 찍는 내용. 개별 수험표(PDF, [AdmissionTicketHtml])와 관리자 일괄 출력(xlsx)이 같은 칸을 쓴다.
 *
 * ponytail: 수험번호는 admin 이 발급해 일괄 출력만 넘겨준다. 개별 수험표는 받을 길이 없어 미발급으로 찍는다.
 * 학생 화면에 수험표 출력이 붙으면 admin 에서 수험번호와 1차 합격 여부를 함께 받아 온다.
 *
 * @property rows 사진 옆에 위에서부터 찍는 이름표와 값
 * @property photo 사진 칸에 넣을 증명사진. 없거나 그 학생이 올린 사진이 아니면 null 이고 칸이 빈다
 */
class AdmissionTicket(
    val title: String,
    val rows: List<Pair<String, String>>,
    val photo: Photo?,
) {
    /** @property contentType 줄인 사진은 `image/jpeg`, 줄이지 못한 사진은 올린 파일의 형식 */
    class Photo(val contentType: String, val bytes: ByteArray)

    companion object {
        const val PRINCIPAL_LINE = "대덕소프트웨어마이스터고등학교장"

        fun of(admissionYear: Int, applicantId: Long, applicant: Applicant, examineeNumber: String?, photo: Photo?) =
            AdmissionTicket(
                title = "${admissionYear}학년도 $SCHOOL_NAME 입학전형 수험표",
                rows = listOf(
                    "수험번호" to (examineeNumber ?: UNISSUED_EXAMINEE_NUMBER),
                    "성명" to applicant.name.orEmpty(),
                    "출신 중학교" to applicant.schoolName.orEmpty(),
                    "지역" to applicant.region?.label.orEmpty(),
                    "전형 유형" to applicant.admissionType?.label.orEmpty(),
                    "접수 번호" to ReceiptNumber.of(applicantId),
                ),
                photo = photo,
            )
    }
}
