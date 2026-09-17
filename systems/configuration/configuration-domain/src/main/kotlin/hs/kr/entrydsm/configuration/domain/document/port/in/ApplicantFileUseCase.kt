package hs.kr.entrydsm.configuration.domain.document.port.`in`

import hs.kr.entrydsm.configuration.domain.document.DownloadableFile
import hs.kr.entrydsm.configuration.domain.document.Requester
import hs.kr.entrydsm.configuration.domain.document.command.UploadFileCommand
import java.io.InputStream

/**
 * 지원자 한 명에게 하나씩 있는 원서·수험표. application 의 applicant id 로 찾고,
 * 본인은 application 이 알려 준 원서 주인 계정이다.
 */
interface ApplicantFileUseCase {
    /** 원서를 올린다. 같은 형식으로 다시 올리면 덮어쓴다. */
    fun uploadApplication(applicantId: Long, command: UploadFileCommand, content: InputStream): DownloadableFile

    /** pdf·hwp 중 최근에 올린 원서. 아직 올리지 않았으면 null. */
    fun findApplication(applicantId: Long, requester: Requester): DownloadableFile?

    /** 수험표 PDF 를 원서 내용으로 새로 만들어 올린다. */
    fun generateAdmissionTicket(applicantId: Long, requester: Requester): DownloadableFile
}
