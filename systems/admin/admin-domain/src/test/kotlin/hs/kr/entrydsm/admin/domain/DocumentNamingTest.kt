package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.document.DocumentNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * configuration 시스템이 실제로 쓰는 키를 그대로 적어 두고 비교합니다.
 *
 * 두 시스템은 서로를 의존하지 않아 컴파일러가 어긋남을 잡아 주지 못합니다. 접두사가
 * 갈리면 admin 의 원서 원본 다운로드가 조용히 404 가 되므로 값으로 못 박습니다.
 * configuration 쪽 규칙은 `FileCategory.objectKeyOf` 와 `FileNaming` 에 있습니다.
 */
class DocumentNamingTest {

    @Test
    fun `원서 원본 키가 configuration 이 올리는 위치와 같다`() {
        assertEquals(
            "dsm_Entry/Backend/application/application_1024.pdf",
            DocumentNaming.applicationDocumentObjectKey(1024),
        )
    }

    @Test
    fun `수험표 키가 configuration 의 카테고리 접두사를 따른다`() {
        assertEquals(
            "dsm_Entry/Backend/admission-ticket/admission_ticket_1024.pdf",
            DocumentNaming.admissionTicketObjectKey(1024),
        )
    }

    @Test
    fun `관리자 산출물도 같은 루트 아래에 둔다`() {
        val keys = listOf(
            DocumentNaming.applicantListObjectKey("job-1"),
            DocumentNaming.admissionTicketBundleObjectKey("job-1"),
        )

        assertTrue(keys.all { it.startsWith(DocumentNaming.KEY_ROOT) })
    }
}
