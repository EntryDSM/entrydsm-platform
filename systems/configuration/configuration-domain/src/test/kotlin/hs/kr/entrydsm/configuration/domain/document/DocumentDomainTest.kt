package hs.kr.entrydsm.configuration.domain.document

import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentDomainTest {

    @Test
    fun `확장자를 대소문자 구분 없이 인식한다`() {
        assertEquals(FileExtension.PDF, FileExtension.fromFileName("application_12.PDF"))
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
            "dsm_Entry/Backend/admission-ticket/admission_ticket_12.pdf",
            FileCategory.ADMISSION_TICKET.objectKeyOf("admission_ticket_12.pdf"),
        )
    }

    @Test
    fun `카테고리는 자기 prefix 아래의 object key만 담는다`() {
        assertTrue(FileCategory.GUIDELINE.holds("dsm_Entry/Backend/guideline/a_guide.pdf"))
        assertFalse(FileCategory.GUIDELINE.holds("dsm_Entry/Backend/application/application_12.pdf"))
    }

    @Test(expected = InvalidFileNameException::class)
    fun `DB에 담을 수 없을 만큼 긴 object key는 거부한다`() {
        FileCategory.ATTACHMENT.objectKeyOf("a".repeat(FileNaming.MAX_STORED_NAME_LENGTH))
    }

    @Test(expected = InvalidFileNameException::class)
    fun `object key에 상위 경로 참조가 들어오면 거부한다`() {
        FileCategory.ATTACHMENT.objectKeyOf("../../etc/passwd")
    }

    @Test
    fun `원서·수험표 파일명은 네 자리 접수번호로 만든다`() {
        assertEquals("application_0012.pdf", FileNaming.applicationFileName(12))
        assertEquals("admission_ticket_0012.pdf", FileNaming.admissionTicketFileName(12))
        assertEquals("application_0001.pdf", FileNaming.applicationFileName(1))
        assertEquals("admission_ticket_0001.pdf", FileNaming.admissionTicketFileName(1))
        // 9999 번을 넘으면 자릿수가 늘어난다.
        assertEquals("application_9999.pdf", FileNaming.applicationFileName(9999))
        assertEquals("admission_ticket_9999.pdf", FileNaming.admissionTicketFileName(9999))
        assertEquals("application_10000.pdf", FileNaming.applicationFileName(10000))
        assertEquals("admission_ticket_10000.pdf", FileNaming.admissionTicketFileName(10000))
    }

    @Test
    fun `증명사진과 첨부파일 이름에 랜덤 토큰을 붙인다`() {
        assertTrue(FileNaming.photoFileName(FileExtension.JPG).matches(Regex("photo_[0-9a-f]{32}\\.jpg")))
        assertTrue(FileNaming.attachmentFileName("guide.pdf").matches(Regex("[0-9a-f]{32}_guide\\.pdf")))
    }

    @Test
    fun `공개 ID는 종류 접두사와 순번이 아닌 임의값이다`() {
        val first = FileNaming.publicId(FileCategory.ATTACHMENT)

        assertTrue(first, first.matches(Regex("attachment_[0-9a-f]{32}")))
        assertFalse(first == FileNaming.publicId(FileCategory.ATTACHMENT))
        assertTrue(FileNaming.publicId(FileCategory.PHOTO).startsWith("photo_"))
    }

    @Test
    fun `원본 파일명에서 경로를 제거하고 허용 외 문자를 치환한다`() {
        assertEquals("pas_swd.pdf", FileNaming.sanitizeOriginalName("../../etc/pas swd.pdf"))
        assertEquals("report.xlsx", FileNaming.sanitizeOriginalName("C:\\temp\\report.xlsx"))
    }

    @Test(expected = InvalidFileNameException::class)
    fun `파일명에 상위 경로 참조가 들어오면 거부한다`() {
        FileNaming.requireSafeFileName("../../etc/passwd")
    }

    @Test
    fun `첨부·요강은 관리자만 적재하고 원서·수험표는 아무도 올리지 않는다`() {
        listOf(FileCategory.ATTACHMENT, FileCategory.GUIDELINE).forEach {
            assertTrue(it.name, it.canStore(admin, ownerUserId = null))
            assertFalse(it.name, it.canStore(student(10), ownerUserId = null))
        }
        listOf(FileCategory.APPLICATION, FileCategory.ADMISSION_TICKET).forEach {
            assertFalse(it.name, it.canStore(admin, ownerUserId = 10))
            assertFalse(it.name, it.canStore(student(10), ownerUserId = 10))
            assertFalse(it.name, it.canDelete(admin, ownerUserId = 10))
        }
    }

    @Test
    fun `증명사진은 학생만 적재하고 올린 학생과 관리자가 받는다`() {
        assertTrue(FileCategory.PHOTO.canStore(student(10), ownerUserId = null))
        assertFalse(FileCategory.PHOTO.canStore(admin, ownerUserId = null))
        assertTrue(FileCategory.PHOTO.canDownload(student(10), ownerUserId = 10))
        assertFalse(FileCategory.PHOTO.canDownload(student(11), ownerUserId = 10))
        assertTrue(FileCategory.PHOTO.canDownload(admin, ownerUserId = 10))
    }

    @Test
    fun `원서와 수험표는 본인 학생과 관리자가 다운로드한다`() {
        assertTrue(FileCategory.APPLICATION.canDownload(student(10), ownerUserId = 10))
        assertFalse(FileCategory.APPLICATION.canDownload(student(11), ownerUserId = 10))
        assertFalse(FileCategory.APPLICATION.canDownload(student(10), ownerUserId = null))
        assertTrue(FileCategory.APPLICATION.canDownload(admin, ownerUserId = 10))

        assertTrue(FileCategory.ADMISSION_TICKET.canDownload(student(10), ownerUserId = 10))
        assertFalse(FileCategory.ADMISSION_TICKET.canDownload(student(11), ownerUserId = 10))
        assertFalse(FileCategory.ADMISSION_TICKET.canDownload(student(10), ownerUserId = null))
        assertTrue(FileCategory.ADMISSION_TICKET.canDownload(admin, ownerUserId = null))
    }

    @Test
    fun `첨부·요강은 모든 학생과 관리자가 다운로드한다`() {
        listOf(FileCategory.ATTACHMENT, FileCategory.GUIDELINE).forEach {
            assertTrue(it.name, it.canDownload(student(10), ownerUserId = null))
            assertTrue(it.name, it.canDownload(admin, ownerUserId = null))
        }
    }

    @Test
    fun `삭제는 적재할 수 있는 사람이 하되 학생은 자기가 올린 파일만 지운다`() {
        assertTrue(FileCategory.ATTACHMENT.canDelete(admin, ownerUserId = null))
        assertFalse(FileCategory.ATTACHMENT.canDelete(student(10), ownerUserId = null))
        assertTrue(FileCategory.PHOTO.canDelete(student(10), ownerUserId = 10))
        assertFalse(FileCategory.PHOTO.canDelete(student(11), ownerUserId = 10))
    }

    private val admin = Requester(1, Requester.Role.ADMIN)

    private fun student(userId: Long) = Requester(userId, Requester.Role.STUDENT)
}
