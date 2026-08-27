package hs.kr.entrydsm.configuration.adapterin.common

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val message: String) {
    INVALID_REQUEST_PARAM(HttpStatus.BAD_REQUEST, "요청 파라미터가 올바르지 않습니다."),
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "허용 용량을 초과했습니다."),
    STORAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "파일 저장에 실패했습니다."),
    PRESIGN_FAILED(HttpStatus.BAD_GATEWAY, "다운로드 URL 발급에 실패했습니다."),
    STORAGE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "파일 저장소에 접근할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
}
