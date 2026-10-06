package hs.kr.entrydsm.admin.domain.exception

import java.util.Collections
import java.util.IdentityHashMap

/** 예외 메시지를 제외하고 원인 체인의 타입과 발생 위치만 기록한다. */
fun Throwable.safeStackTrace(): String {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    return generateSequence(this) { it.cause }.takeWhile(visited::add)
        .joinToString("\nCaused by ") { "${it.javaClass.name}\n${it.stackTrace.joinToString("\n")}" }
}
