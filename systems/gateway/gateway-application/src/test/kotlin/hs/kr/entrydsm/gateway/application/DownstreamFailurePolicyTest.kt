package hs.kr.entrydsm.gateway.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

class DownstreamFailurePolicyTest {
    @Test
    fun classifiesTimeoutAndConnectionFailures() {
        assertEquals(DownstreamFailureType.TIMEOUT, DownstreamFailurePolicy.classify(TimeoutException()))
        assertEquals(DownstreamFailureType.TIMEOUT, DownstreamFailurePolicy.classify(SocketTimeoutException()))
        assertEquals(DownstreamFailureType.CONNECTION, DownstreamFailurePolicy.classify(ConnectException()))
    }

    @Test
    fun classifiesNestedAndNamedFailures() {
        assertEquals(
            DownstreamFailureType.TIMEOUT,
            DownstreamFailurePolicy.classify(IllegalStateException(SocketTimeoutException())),
        )
        assertEquals(
            DownstreamFailureType.CONNECTION,
            DownstreamFailurePolicy.classify(NamedConnectFailure()),
        )
    }

    @Test
    fun leavesUnknownFailuresUnclassified() {
        assertEquals(null, DownstreamFailurePolicy.classify(IllegalStateException()))
        assertEquals(null, DownstreamFailurePolicy.classify(IllegalStateException(IllegalArgumentException())))
    }

    private class NamedConnectFailure : RuntimeException()
}
