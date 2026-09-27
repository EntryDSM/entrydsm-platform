package hs.kr.entrydsm.configuration.application

import hs.kr.entrydsm.configuration.domain.document.AdmissionTicket
import hs.kr.entrydsm.configuration.domain.document.Applicant
import hs.kr.entrydsm.configuration.domain.document.ApplicationForm
import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.FileCategory
import hs.kr.entrydsm.configuration.domain.document.FileDocument
import hs.kr.entrydsm.configuration.domain.document.FileNaming
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.StoredObject
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import hs.kr.entrydsm.configuration.domain.document.exception.ApplicantNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.DocumentAccessDeniedException
import hs.kr.entrydsm.configuration.domain.document.exception.FileDocumentNotFoundException
import hs.kr.entrydsm.configuration.domain.document.exception.FileTooLargeException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileFormatException
import hs.kr.entrydsm.configuration.domain.document.exception.InvalidFileNameException
import hs.kr.entrydsm.configuration.domain.document.exception.StorageUnavailableException
import hs.kr.entrydsm.configuration.domain.document.port.out.AdmissionTicketSheetPort
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicantPort
import hs.kr.entrydsm.configuration.domain.document.port.out.ApplicationFormPdfPort
import hs.kr.entrydsm.configuration.domain.document.port.out.FileDocumentRepository
import hs.kr.entrydsm.configuration.domain.document.port.out.PdfRenderPort
import hs.kr.entrydsm.configuration.domain.document.port.out.StoragePort
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import javax.imageio.ImageIO

class FileDocumentServiceTest {

    private val storage = FakeStoragePort()
    private val repository = FakeFileDocumentRepository()
    private val applicants = mutableMapOf(APPLICANT_ID to applicant())
    private val forms = mutableMapOf(STUDENT_ID to applicationForm())
    private val pdf = RecordingPdfRenderPort()
    private val formPdf = RecordingApplicationFormPdfPort()
    private val sheet = RecordingAdmissionTicketSheetPort()
    private val service = FileDocumentService(
        storage, repository, presignExpirySeconds = 300,
        // 수험표는 applicant id 로, 원서 전문은 계정으로 찾는다.
        applicantPort = object : ApplicantPort {
            override fun findById(applicantId: Long) = applicants[applicantId]

            override fun findApplicationForm(accountId: Long) = forms[accountId]
        },
        pdfRenderPort = pdf, applicationFormPdfPort = formPdf, admissionTicketSheetPort = sheet, admissionYear = 2027,
    )

    private val admin = Requester(1, Requester.Role.ADMIN)

    @Test
    fun `원서는 요청자 계정으로 찾아 접수번호 파일명으로 만들고 서명 URL을 붙여 돌려준다`() {
        val generated = service.generateApplicationForm(student(STUDENT_ID))

        // 파일명은 계정(10)이 아니라 원서가 알려 준 접수번호(12)로 짓는다.
        assertEquals("dsm_Entry/backend/stag/application/application_0012.pdf", generated.document.objectKey)
        assertEquals("application/pdf", generated.document.contentType)
        assertEquals(STUDENT_ID, generated.document.ownerUserId)
        assertEquals("https://s3/dsm_Entry/backend/stag/application/application_0012.pdf?expires=300", generated.downloadUrl)
        assertEquals(300L, generated.expiresIn)
        assertEquals("홍길동", formPdf.lastForm?.name)
    }

    @Test
    fun `원서에는 그 학생이 올린 사진만 넣는다`() {
        val own = service.upload(photo(student(STUDENT_ID)), content())
        val others = service.upload(photo(student(11)), content())

        forms[STUDENT_ID] = applicationForm().copy(photoFileId = own.document.publicId)
        service.generateApplicationForm(student(STUDENT_ID))
        // 가짜 저장소는 object key 를 내용으로 돌려준다.
        assertArrayEquals(own.document.objectKey.toByteArray(), formPdf.lastPhoto)

        forms[STUDENT_ID] = applicationForm().copy(photoFileId = others.document.publicId)
        service.generateApplicationForm(student(STUDENT_ID))
        assertNull(formPdf.lastPhoto)
    }

    @Test
    fun `원서가 없는 계정은 404다 - 관리자 계정도 자기 원서는 없다`() {
        // 계정으로 찾는 쪽이라 남의 계정을 훑을 수 없다. 없는 것은 숨길 이유가 없어 404 다.
        assertThrows(ApplicantNotFoundException::class.java) { service.generateApplicationForm(student(11)) }
        // 관리자는 권한을 통과하지만 관리자 계정에 원서가 없다. applicant id 를 받는 쪽을 써야 한다.
        assertThrows(ApplicantNotFoundException::class.java) { service.generateApplicationForm(admin) }

        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `관리자는 applicant id 로 지목한 지원자의 원서를 받고, 파일 주인은 그 지원자다`() {
        val generated = service.generateApplicationForm(APPLICANT_ID, admin)

        assertEquals("dsm_Entry/backend/stag/application/application_0012.pdf", generated.document.objectKey)
        assertEquals(STUDENT_ID, generated.document.ownerUserId)
        assertEquals("홍길동", formPdf.lastForm?.name)
    }

    @Test
    fun `applicant id 로 받는 원서는 남의 것이면 403, 관리자에게 없는 지원자면 404다`() {
        // 본인 지원자는 applicant id 로도 받을 수 있다.
        service.generateApplicationForm(APPLICANT_ID, student(STUDENT_ID))

        assertThrows(DocumentAccessDeniedException::class.java) { service.generateApplicationForm(APPLICANT_ID, student(11)) }
        // 권한을 먼저 보므로 학생은 없는 지원자도 403 이라 applicant id 를 훑을 수 없다.
        assertThrows(DocumentAccessDeniedException::class.java) { service.generateApplicationForm(999, student(11)) }
        assertThrows(ApplicantNotFoundException::class.java) { service.generateApplicationForm(999, admin) }
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `카테고리가 허용하지 않는 확장자는 거부한다`() {
        service.upload(photo(student(STUDENT_ID), originalName = "사진.pdf"), content())
    }

    @Test(expected = InvalidFileFormatException::class)
    fun `확장자가 없으면 거부한다`() {
        service.upload(attachment(originalName = "공지"), content())
    }

    @Test(expected = FileTooLargeException::class)
    fun `카테고리 용량 한도를 넘으면 거부한다`() {
        service.upload(attachment(sizeBytes = FileCategory.ATTACHMENT.maxSizeBytes + 1), content())
    }

    @Test
    fun `원본 파일명이 DB에 담을 수 없을 만큼 길면 저장소에 올리기 전에 거부한다`() {
        val longName = "a".repeat(FileNaming.MAX_STORED_NAME_LENGTH) + ".pdf"

        assertThrows(InvalidFileNameException::class.java) {
            service.upload(attachment(originalName = longName), content())
        }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `요청마다 새 키를 쓰는 파일은 메타데이터 저장이 실패하면 올린 객체를 지운다`() {
        repository.failingSaves = 1

        assertThrows(IllegalStateException::class.java) { service.upload(attachment(), content()) }

        assertEquals(storage.uploaded, storage.deleted)
    }

    @Test
    fun `보상 삭제가 실패해도 원래 예외를 그대로 올린다`() {
        repository.failingSaves = 1
        storage.failOnDelete = true

        val error = runCatching { service.upload(attachment(), content()) }.exceptionOrNull()

        assertEquals("save failed", error?.message)
    }

    @Test
    fun `서명 URL 발급이 실패하면 아무것도 올리거나 저장하지 않는다`() {
        storage.failOnPresign = true

        assertThrows(StorageUnavailableException::class.java) { service.upload(attachment(), content()) }
        assertThrows(StorageUnavailableException::class.java) { service.generateAdmissionTicket(APPLICANT_ID, admin) }

        assertTrue(storage.uploaded.isEmpty())
        assertEquals(0L, repository.count(FileCategory.ATTACHMENT) + repository.count(FileCategory.ADMISSION_TICKET))
    }

    @Test
    fun `원서는 동시 요청이 행을 먼저 만들어 저장이 실패하면 객체를 지우지 않고 다시 저장한다`() {
        repository.failingSaves = 1

        val generated = service.generateApplicationForm(student(STUDENT_ID))

        assertTrue(storage.deleted.isEmpty())
        assertEquals(generated.document.objectKey, repository.findByObjectKey(generated.document.objectKey)?.objectKey)
    }

    @Test
    fun `원서·수험표는 다시 저장해도 실패하면 예외를 올리되 같은 키의 객체는 지우지 않는다`() {
        repository.failingSaves = Int.MAX_VALUE

        assertThrows(IllegalStateException::class.java) { service.generateApplicationForm(student(STUDENT_ID)) }
        assertThrows(IllegalStateException::class.java) { service.generateAdmissionTicket(APPLICANT_ID, admin) }

        assertTrue(storage.deleted.isEmpty())
    }

    @Test
    fun `수험표는 지원자 정보와 본인 사진으로 만들고 수험번호는 미발급으로 찍는다`() {
        val photo = service.upload(photo(student(STUDENT_ID)), content()).withPng()
        applicants[APPLICANT_ID] = Applicant(
            STUDENT_ID, "홍<길동>", "대덕중학교", Applicant.Region.DAEJEON, Applicant.AdmissionType.MEISTER, photo.document.publicId,
        )

        val ticket = service.generateAdmissionTicket(APPLICANT_ID, student(STUDENT_ID))

        assertEquals("dsm_Entry/backend/stag/admission-ticket/admission_ticket_0012.pdf", ticket.document.objectKey)
        assertEquals("application/pdf", ticket.document.contentType)
        assertEquals(STUDENT_ID, ticket.document.ownerUserId)
        assertTrue(ticket.downloadUrl.contains("admission_ticket_0012.pdf"))
        listOf("2027학년도", "미발급", "홍&lt;길동&gt;", "대덕중학교", "대전", "마이스터전형", "접수 번호", "0012", "data:image/png;base64,")
            .forEach { assertTrue(it, it in pdf.lastHtml) }
    }

    @Test
    fun `관리자 일괄 출력은 받은 순서대로 넘어온 수험번호를 찍어 xlsx 하나로 만들고 저장소에 올리지 않는다`() {
        applicants[13] = applicant()

        val xlsx = service.renderAdmissionTickets(listOf(13L to "100002", APPLICANT_ID to null))

        assertArrayEquals("xlsx".toByteArray(), xlsx)
        val rows = sheet.lastTickets.map { it.rows.toMap() }
        assertEquals(listOf("100002", "미발급"), rows.map { it["수험번호"] })
        assertEquals(listOf("0013", "0012"), rows.map { it["접수 번호"] })
        assertEquals("2027학년도 대덕소프트웨어마이스터고등학교 입학전형 수험표", sheet.lastTickets.first().title)
        assertTrue(storage.uploaded.isEmpty())
        assertThrows(ApplicantNotFoundException::class.java) {
            service.renderAdmissionTickets(listOf(APPLICANT_ID to "100001", 404L to "100003"))
        }
    }

    @Test
    fun `큰 증명사진은 수험표 칸 폭으로 줄여 흰 바탕 JPEG로 넣고 작은 사진은 그대로 넣는다`() {
        val large = service.upload(photo(student(STUDENT_ID)), content())
        // 투명 PNG. 휴대전화 사진처럼 크다.
        storage.contents[large.document.objectKey] = image(BufferedImage(1800, 2400, BufferedImage.TYPE_INT_ARGB), "png")
        applicants[APPLICANT_ID] = applicant(photoFileId = large.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        val fitted = ImageIO.read(ByteArrayInputStream(sheet.lastPhoto().bytes))
        assertEquals("image/jpeg", sheet.lastPhoto().contentType)
        assertEquals(600 to 800, fitted.width to fitted.height)
        val corner = Color(fitted.getRGB(0, 0))
        assertTrue("$corner", minOf(corner.red, corner.green, corner.blue) > 250)

        val small = service.upload(photo(student(STUDENT_ID)), content())
        val original = image(BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "png")
        storage.contents[small.document.objectKey] = original
        applicants[APPLICANT_ID] = applicant(photoFileId = small.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        assertEquals("image/png", sheet.lastPhoto().contentType)
        assertArrayEquals(original, sheet.lastPhoto().bytes)
    }

    @Test
    fun `칸보다 작은 투명 사진도 흰 바탕 JPEG로 넣어 사진 칸의 회색이 비치지 않게 한다`() {
        val transparent = service.upload(photo(student(STUDENT_ID)), content())
        storage.contents[transparent.document.objectKey] =
            image(BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB), "png")
        applicants[APPLICANT_ID] = applicant(photoFileId = transparent.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        val flattened = ImageIO.read(ByteArrayInputStream(sheet.lastPhoto().bytes))
        assertEquals("image/jpeg", sheet.lastPhoto().contentType)
        assertEquals(300 to 400, flattened.width to flattened.height)
        val corner = Color(flattened.getRGB(0, 0))
        assertTrue("$corner", minOf(corner.red, corner.green, corner.blue) > 250)
    }

    @Test
    fun `못 읽는 사진은 줄일 수도 확인할 수도 없어 수험표에 넣지 않는다`() {
        val webp = service.upload(photo(student(STUDENT_ID), "사진.webp"), content())
        storage.contents[webp.document.objectKey] = "RIFF0000WEBPVP8 ".toByteArray() + ByteArray(5 * 1024 * 1024)
        applicants[APPLICANT_ID] = applicant(photoFileId = webp.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        assertNull(sheet.lastTickets.single().photo)
    }

    @Test
    fun `칸 안의 사진도 300KB 를 넘거나 JPEG·PNG 가 아니면 JPEG 로 다시 저장한다`() {
        val heavy = service.upload(photo(student(STUDENT_ID), "사진.jpg"), content())
        // 사진은 300×400 인데 메타데이터처럼 뒤에 400KB 가 붙었다.
        storage.contents[heavy.document.objectKey] =
            image(BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB), "jpg") + ByteArray(400 * 1024)
        applicants[APPLICANT_ID] = applicant(photoFileId = heavy.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        val slimmed = sheet.lastPhoto()
        assertEquals("image/jpeg", slimmed.contentType)
        assertTrue("${slimmed.bytes.size}", slimmed.bytes.size <= 300 * 1024)
        assertEquals(300 to 400, ImageIO.read(ByteArrayInputStream(slimmed.bytes)).let { it.width to it.height })

        // 이름은 png 인데 내용은 BMP 다. xlsx 는 BMP 를 담지 못한다.
        val bitmap = service.upload(photo(student(STUDENT_ID)), content())
        storage.contents[bitmap.document.objectKey] = image(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "bmp")
        applicants[APPLICANT_ID] = applicant(photoFileId = bitmap.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        assertEquals("image/jpeg", sheet.lastPhoto().contentType)
        assertEquals(listOf(0xFF.toByte(), 0xD8.toByte()), sheet.lastPhoto().bytes.take(2))
    }

    @Test
    fun `세로로 긴 사진은 높이 800 에 맞춰 칸 안으로 줄인다`() {
        val tall = service.upload(photo(student(STUDENT_ID)), content())
        storage.contents[tall.document.objectKey] = image(BufferedImage(600, 3000, BufferedImage.TYPE_INT_RGB), "png")
        applicants[APPLICANT_ID] = applicant(photoFileId = tall.document.publicId)

        service.renderAdmissionTickets(listOf(APPLICANT_ID to null))

        val fitted = ImageIO.read(ByteArrayInputStream(sheet.lastPhoto().bytes))
        assertEquals(160 to 800, fitted.width to fitted.height)
    }

    @Test
    fun `남의 지원자 수험표는 없어도 403이고, 관리자는 없는 지원자면 404다`() {
        assertThrows(DocumentAccessDeniedException::class.java) { service.generateAdmissionTicket(APPLICANT_ID, student(11)) }
        assertThrows(DocumentAccessDeniedException::class.java) { service.generateAdmissionTicket(404, student(11)) }
        assertThrows(ApplicantNotFoundException::class.java) { service.generateAdmissionTicket(404, admin) }
        assertTrue(storage.uploaded.isEmpty())
    }

    @Test
    fun `원서에 다른 학생의 사진이나 사진이 아닌 파일 ID가 적혀 있으면 수험표에 넣지 않는다`() {
        val othersPhoto = service.upload(photo(student(11)), content()).withPng()
        val attachment = service.upload(attachment(), content()).withPng()

        applicants[APPLICANT_ID] = applicant(photoFileId = othersPhoto.document.publicId)
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertFalse("data:" in pdf.lastHtml)

        applicants[APPLICANT_ID] = applicant(photoFileId = attachment.document.publicId)
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertFalse("data:" in pdf.lastHtml)
    }

    @Test
    fun `V006 전에 숫자로 저장된 사진 ID도 그 학생이 올린 사진이면 수험표에 넣는다`() {
        val own = service.upload(photo(student(STUDENT_ID)), content()).withPng()
        val others = service.upload(photo(student(11)), content()).withPng()

        applicants[APPLICANT_ID] = applicant(photoFileId = own.document.id.toString())
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertTrue("data:image/png;base64," in pdf.lastHtml)

        applicants[APPLICANT_ID] = applicant(photoFileId = others.document.id.toString())
        service.generateAdmissionTicket(APPLICANT_ID, admin)
        assertFalse("data:" in pdf.lastHtml)
    }

    @Test
    fun `증명사진은 학생이 올리고 공개 ID로 본인과 관리자가 받는다`() {
        val photo = service.upload(photo(student(STUDENT_ID)), content())
        val photoId = photo.document.publicId

        assertTrue(photo.document.objectKey.matches(Regex("dsm_Entry/backend/stag/photo/photo_[0-9a-f]{32}\\.png")))
        assertTrue(photoId.matches(Regex("photo_[0-9a-f]{32}")))
        assertEquals(photo.document.objectKey, service.find(FileCategory.PHOTO, photoId, student(STUDENT_ID)).document.objectKey)
        assertEquals(photo.document.objectKey, service.find(FileCategory.PHOTO, photoId, admin).document.objectKey)
        assertThrows(DocumentAccessDeniedException::class.java) { service.find(FileCategory.PHOTO, photoId, student(11)) }
        assertThrows(DocumentAccessDeniedException::class.java) { service.upload(photo(admin), content()) }
    }

    @Test
    fun `첨부는 관리자만 올리고 원본 파일명을 남긴다`() {
        val attachment = service.upload(attachment(), content())

        assertEquals("notice.pdf", attachment.document.originalName)
        assertTrue(attachment.document.objectKey.matches(Regex("dsm_Entry/backend/stag/attachment/[0-9a-f]{32}_notice\\.pdf")))
        assertThrows(DocumentAccessDeniedException::class.java) { service.upload(attachment(student(STUDENT_ID)), content()) }
    }

    @Test
    fun `공개 ID로 찾을 때 다른 종류의 파일이나 없는 ID는 없는 것으로 본다`() {
        val attachment = service.upload(attachment(), content())

        assertThrows(FileDocumentNotFoundException::class.java) {
            service.find(FileCategory.GUIDELINE, attachment.document.publicId, admin)
        }
        assertThrows(FileDocumentNotFoundException::class.java) { service.find(FileCategory.ATTACHMENT, "attachment_1", admin) }
    }

    @Test
    fun `요강 목록은 최근 것부터 페이지로 주고 전체 개수를 센다`() {
        val older = service.upload(guideline("2026.pdf"), content())
        val newer = service.upload(guideline("2027.pdf"), content())
        service.upload(attachment(), content())

        val first = service.findPage(FileCategory.GUIDELINE, page = 1, size = 1, requester = student(STUDENT_ID))
        val second = service.findPage(FileCategory.GUIDELINE, page = 2, size = 1, requester = admin)

        assertEquals(listOf(newer.document.publicId), first.items.map { it.document.publicId })
        assertEquals(listOf(older.document.publicId), second.items.map { it.document.publicId })
        assertEquals(2L, first.totalElements)
        assertTrue(first.items.single().downloadUrl.startsWith("https://s3/dsm_Entry/backend/stag/guideline/"))
    }

    @Test
    fun `첨부 삭제는 관리자만 하고 행과 객체를 모두 지운다`() {
        val attachment = service.upload(attachment(), content())
        val attachmentId = attachment.document.publicId

        assertThrows(DocumentAccessDeniedException::class.java) {
            service.delete(FileCategory.ATTACHMENT, attachmentId, student(STUDENT_ID))
        }

        service.delete(FileCategory.ATTACHMENT, attachmentId, admin)

        assertNull(repository.findByPublicId(attachmentId))
        assertEquals(listOf(attachment.document.objectKey), storage.deleted)
        assertThrows(FileDocumentNotFoundException::class.java) { service.delete(FileCategory.ATTACHMENT, attachmentId, admin) }
    }

    @Test
    fun `저장소 객체 삭제가 실패해도 행은 지우고 예외를 올리지 않는다`() {
        val attachmentId = service.upload(attachment(), content()).document.publicId
        storage.failOnDelete = true

        service.delete(FileCategory.ATTACHMENT, attachmentId, admin)

        assertNull(repository.findByPublicId(attachmentId))
    }

    private fun student(userId: Long) = Requester(userId, Requester.Role.STUDENT)

    private fun photo(requester: Requester, originalName: String = "사진.png") =
        UploadFileCommand(FileCategory.PHOTO, originalName, 1024, requester)

    private fun attachment(
        requester: Requester = admin,
        originalName: String = "notice.pdf",
        sizeBytes: Long = 1024,
    ) = UploadFileCommand(FileCategory.ATTACHMENT, originalName, sizeBytes, requester)

    private fun guideline(originalName: String) = UploadFileCommand(FileCategory.GUIDELINE, originalName, 1024, admin)

    private fun content(): InputStream = ByteArrayInputStream(ByteArray(4))

    private fun image(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    /** 가짜 저장소는 넣은 내용이 없으면 object key 를 돌려준다. 수험표에는 읽히는 사진만 들어가므로 PNG 를 넣는다. */
    private fun DownloadableFile.withPng(): DownloadableFile = also {
        storage.contents[document.objectKey] = image(BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "png")
    }

    private companion object {
        const val APPLICANT_ID = 12L
        const val STUDENT_ID = 10L

        fun applicant(photoFileId: String? = null) = Applicant(STUDENT_ID, "홍길동", null, null, null, photoFileId)

        /** 작성 중인 원서처럼 이름만 채운다. 나머지 칸이 비어도 서식은 찍힌다. */
        fun applicationForm() = ApplicationForm(
            applicantId = APPLICANT_ID, userId = STUDENT_ID, name = "홍길동", phoneNumber = null, birthdate = null,
            gender = null, address = null, photoFileId = null, region = null, admissionType = null, specialNote = null,
            graduationType = null, graduationDate = null, guardianName = null, guardianRelation = null,
            guardianPhoneNumber = null, school = null, semesterGrades = emptyList(), academicRecord = null,
            introduction = null, studyPlan = null,
        )
    }

    private class FakeStoragePort : StoragePort {
        val uploaded = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        /** 내용을 따로 넣지 않은 객체는 object key 를 내용으로 돌려준다. */
        val contents = mutableMapOf<String, ByteArray>()
        var failOnDelete = false
        var failOnPresign = false

        override fun upload(
            objectKey: String,
            contentType: String,
            sizeBytes: Long,
            content: InputStream,
        ): StoredObject {
            uploaded += objectKey
            return StoredObject(bucket = "entrydsm", objectKey = objectKey, checksum = "abc")
        }

        override fun issueDownloadUrl(objectKey: String, expiresInSeconds: Long): String {
            if (failOnPresign) throw StorageUnavailableException("presign", objectKey)
            return "https://s3/$objectKey?expires=$expiresInSeconds"
        }

        override fun download(objectKey: String): ByteArray = contents[objectKey] ?: objectKey.toByteArray()

        override fun delete(objectKey: String) {
            if (failOnDelete) throw IllegalStateException("delete failed")
            deleted += objectKey
        }
    }

    private class RecordingPdfRenderPort : PdfRenderPort {
        var lastHtml = ""

        override fun render(html: String): ByteArray {
            lastHtml = html
            return "%PDF-".toByteArray()
        }
    }

    private class RecordingAdmissionTicketSheetPort : AdmissionTicketSheetPort {
        var lastTickets = emptyList<AdmissionTicket>()

        fun lastPhoto(): AdmissionTicket.Photo = checkNotNull(lastTickets.single().photo)

        override fun render(tickets: List<AdmissionTicket>): ByteArray {
            lastTickets = tickets
            return "xlsx".toByteArray()
        }
    }

    private class RecordingApplicationFormPdfPort : ApplicationFormPdfPort {
        var lastForm: ApplicationForm? = null
        var lastPhoto: ByteArray? = null

        override fun render(form: ApplicationForm, photo: ByteArray?): ByteArray {
            lastForm = form
            lastPhoto = photo
            return "%PDF-".toByteArray()
        }
    }

    /** 실제 어댑터처럼 같은 object key 는 새 행 대신 기존 행을 갱신하고 공개 ID 를 유지한다. */
    private class FakeFileDocumentRepository : FileDocumentRepository {
        private val saved = mutableListOf<FileDocument>()
        /** 이 횟수만큼 저장이 실패한다. */
        var failingSaves = 0
        private var clock = 0L

        override fun save(fileDocument: FileDocument): FileDocument {
            if (failingSaves > 0) {
                failingSaves--
                throw IllegalStateException("save failed")
            }
            val existing = findByObjectKey(fileDocument.objectKey)
            saved.removeIf { it.objectKey == fileDocument.objectKey }
            return fileDocument.copy(
                id = existing?.id ?: ((saved.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1),
                publicId = existing?.publicId ?: fileDocument.publicId,
                createdAt = Instant.EPOCH.plusSeconds(++clock),
            ).also { saved += it }
        }

        override fun findByObjectKey(objectKey: String): FileDocument? = saved.firstOrNull { it.objectKey == objectKey }

        override fun findByPublicId(publicId: String): FileDocument? = saved.firstOrNull { it.publicId == publicId }

        override fun findById(id: Long): FileDocument? = saved.firstOrNull { it.id == id }

        override fun findPage(category: FileCategory, page: Int, size: Int): List<FileDocument> =
            saved.filter { category.holds(it.objectKey) }
                .sortedByDescending { it.createdAt }
                .drop((page - 1) * size)
                .take(size)

        override fun count(category: FileCategory): Long = saved.count { category.holds(it.objectKey) }.toLong()

        override fun deleteByObjectKey(objectKey: String) {
            saved.removeIf { it.objectKey == objectKey }
        }
    }
}
