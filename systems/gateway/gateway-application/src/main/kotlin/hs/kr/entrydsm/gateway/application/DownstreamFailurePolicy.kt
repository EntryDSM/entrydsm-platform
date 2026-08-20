package hs.kr.entrydsm.gateway.application

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

enum class DownstreamFailureType {
    TIMEOUT,
    CONNECTION,
}

object DownstreamFailurePolicy {
    fun classify(error: Throwable): DownstreamFailureType? = when {
        error is TimeoutException || error is SocketTimeoutException || error.isNamed("Timeout") -> {
            DownstreamFailureType.TIMEOUT
        }
        error is ConnectException || error.isNamed("Connect") -> DownstreamFailureType.CONNECTION
        error.cause != null -> classify(error.cause!!)
        else -> null
    }

    private fun Throwable.isNamed(fragment: String): Boolean =
        javaClass.simpleName.contains(fragment, ignoreCase = true)
}
