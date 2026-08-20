package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.application.port.out.data.ApplicationStateChangedEvent

interface ApplicationEventConsumer {
    /** Returns true only when the event advanced the local projection. */
    fun consume(event: ApplicationStateChangedEvent): Boolean
}
