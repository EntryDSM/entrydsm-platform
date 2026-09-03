package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DocumentDomainTest {

    @Test
    fun `확장자를 대소문자 구분 없이 인식한다`() {
        assertEquals(FileExtension.PDF, FileExtension.fromFileName("application_1001.PDF"))
        assertEquals(FileExtension.XLSX, FileExtension.fromExtension(".xlsx"))
    }

    @Test
    fun `jpeg는 jpg의 별칭으로 처리한다`() {
        assertEquals(FileExtension.JPG, FileExtension.fromFileName("photo.jpeg"))
        assertEquals("image/jpeg", FileExtension.JPG.contentType)
    }

    @Test
    fun `확장자가 없으면 인식하지 않는다`() {
        assertNull(FileExtension.fromFileName("noextension"))
        assertNull(FileExtension.fromFileName("unknown.exe"))
    }

    @Test
    fun `카테고리마다 허용 확장자가 다르다`() {
        assertTrue(FileCategory.APPLICATION.supports(FileExtension.PDF))
        assertFalse(FileCategory.APPLICATION.supports(FileExtension.JPG))
        assertEquals(setOf(FileExtension.XLSX), FileCategory.APPLICANT_LIST.allowedExtensions)
        assertTrue(FileCategory.PHOTO.supports(FileExtension.WEBP))
        assertFalse(FileCategory.PHOTO.supports(FileExtension.PDF))
        assertTrue(FileCategory.ATTACHMENT.supports(FileExtension.DOCX))
    }

    @Test
    fun `카테고리별 용량 한도를 넘으면 초과로 판정한다`() {
        assertFalse(FileCategory.PHOTO.exceedsMaxSize(FileCategory.PHOTO.maxSizeBytes))
        assertTrue(FileCategory.PHOTO.exceedsMaxSize(FileCategory.PHOTO.maxSizeBytes + 1))
    }

    @Test
    fun `object key는 루트 prefix와 카테고리 prefix를 붙인다`() {
        assertEquals(
            "dsm_Entry/Backend/admission-ticket/admission_ticket_1001.pdf",
            FileCategory.ADMISSION_TICKET.objectKeyOf("admission_ticket_1001.pdf"),
        )
    }

    @Test(expected = InvalidFileNameException::class)
    fun `object key에 상위 경로 참조가 들어오면 거부한다`() {
        FileCategory.APPLICANT_LIST.objectKeyOf("../../etc/passwd")
    }

    @Test
    fun `명세 예시와 같은 파일명을 만든다`() {
        assertEquals(
            "application_1001.pdf",
            FileNaming.applicationFileName("1001", FileExtension.PDF),
        )
        assertEquals(
            "admission_ticket_1001.pdf",
            FileNaming.admissionTicketFileName("1001", FileExtension.PDF),
        )
        assertEquals(
            "applicants_20260726.xlsx",
            FileNaming.applicantListFileName(LocalDate.of(2026, 7, 26)),
        )
    }

    @Test
    fun `증명사진과 첨부파일 이름에 랜덤 토큰을 붙인다`() {
        assertTrue(FileNaming.photoFileName(FileExtension.JPG).matches(Regex("photo_[0-9a-f]{32}\\.jpg")))
        assertTrue(FileNaming.attachmentFileName("guide.pdf").matches(Regex("[0-9a-f]{32}_guide\\.pdf")))
    }

    @Test
    fun `원본 파일명에서 경로를 제거하고 허용 외 문자를 치환한다`() {
        assertEquals("pas_swd.pdf", FileNaming.sanitizeOriginalName("../../etc/pas swd.pdf"))
        assertEquals("report.xlsx", FileNaming.sanitizeOriginalName("C:\\temp\\report.xlsx"))
    }

    @Test(expected = InvalidFileNameException::class)
    fun `수험번호에 경로 문자가 들어오면 거부한다`() {
        FileNaming.requireIdentifier("../1001")
    }

    @Test(expected = InvalidFileNameException::class)
    fun `다운로드 파일명에 상위 경로 참조가 들어오면 거부한다`() {
        FileNaming.requireSafeFileName("../../etc/passwd")
    }

    @Test
    fun `정상 다운로드 파일명은 그대로 통과시킨다`() {
        assertEquals(
            "applicants_20260726.xlsx",
            FileNaming.requireSafeFileName("applicants_20260726.xlsx"),
        )
    }
}
