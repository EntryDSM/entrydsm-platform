package hs.kr.entrydsm.notification.adapterin.grpc

import hs.kr.entrydsm.notification.application.port.`in`.CreateNoticeUseCase
import hs.kr.entrydsm.notification.application.port.`in`.command.CreateNoticeCommand
import hs.kr.entrydsm.notification.application.port.`in`.result.NoticeDetailResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGrpcServerTest {
    /**
     * 포트 0 은 비어 있는 포트를 고르게 합니다. 주입된 포트로 실제 기동·종료가 되는지만 봅니다.
     */
    @Test
    fun startsAndStopsWithTheInjectedPort() {
        val server = NotificationGrpcServer(0, NotificationGrpcService(UnusedCreateNoticeUseCase()))

        assertFalse(server.isRunning())

        server.start()
        assertTrue(server.isRunning())

        server.stop()
        assertFalse(server.isRunning())
    }

    private class UnusedCreateNoticeUseCase : CreateNoticeUseCase {
        override fun createNotice(command: CreateNoticeCommand): NoticeDetailResult =
            throw UnsupportedOperationException("수명주기만 검증합니다")
    }
}
