package hs.kr.entrydsm.configuration.domain.document

/** 파일을 적재하거나 다운로드할 수 있는 주체. [FileCategory] 의 권한표에 쓴다. */
enum class FileAccessor {
    ADMIN,

    /** 모든 학생 */
    STUDENT,

    /** 본인 학생. 원서·수험표는 그 수험번호로 원서를 적재한 학생, 그 밖의 파일은 올린 학생이다. */
    OWNER,
}
