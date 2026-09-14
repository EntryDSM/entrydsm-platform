package hs.kr.entrydsm.configuration.domain.document

/** 게이트웨이가 인증해 넘긴 요청자(X-User-Id, X-User-Role). */
data class Requester(
    val userId: Long,
    val role: Role,
) {
    /** 학생이면 사용자 id, 관리자면 null. 적재한 파일의 본인으로 남기는 값이다. */
    val studentId: Long?
        get() = userId.takeIf { role == Role.STUDENT }

    enum class Role { ADMIN, STUDENT }
}
