package hs.kr.entrydsm.admin.domain

import hs.kr.entrydsm.admin.domain.document.AdmissionTicketHtml
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.Region
import hs.kr.entrydsm.admin.domain.model.AdmissionTicket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmissionTicketHtmlTest {

    private fun ticket(
        examineeNumber: String? = null,
        name: String = "홍길동",
    ) = AdmissionTicket(
        admissionYear = 2027,
        receiptNumber = 2,
        examineeNumber = examineeNumber,
        name = name,
        schoolName = "서울중학교",
        region = Region.NATIONWIDE,
        admissionType = AdmissionType.GENERAL,
    )

    @Test
    fun `수험표에 학년도와 학교장 서명을 인쇄한다`() {
        val html = AdmissionTicketHtml.render(ticket())

        assertTrue(html.contains("2027학년도 대덕소프트웨어마이스터고등학교 입학전형 수험표"))
        assertTrue(html.contains("대덕소프트웨어마이스터고등학교장"))
    }

    @Test
    fun `수험 번호가 없으면 미발급으로 인쇄한다`() {
        assertTrue(AdmissionTicketHtml.render(ticket()).contains("미발급"))
    }

    @Test
    fun `수험 번호가 있으면 번호를 그대로 인쇄한다`() {
        val html = AdmissionTicketHtml.render(ticket(examineeNumber = "100001"))

        assertTrue(html.contains("100001"))
        assertFalse(html.contains("미발급"))
    }

    @Test
    fun `지역과 전형 유형을 한글 표기로 인쇄한다`() {
        val html = AdmissionTicketHtml.render(ticket())

        assertTrue(html.contains("전국"))
        assertTrue(html.contains("일반전형"))
    }

    @Test
    fun `이름에 들어온 태그를 이스케이프해 마크업을 깨지 않는다`() {
        val html = AdmissionTicketHtml.render(ticket(name = "<script>alert(1)</script>"))

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }
}
