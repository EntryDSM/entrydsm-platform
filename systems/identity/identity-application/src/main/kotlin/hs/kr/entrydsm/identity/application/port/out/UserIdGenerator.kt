package hs.kr.entrydsm.identity.application.port.out

fun interface UserIdGenerator {
    fun nextId(): Long
}
