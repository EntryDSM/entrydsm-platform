package hs.kr.entrydsm.configuration.domain.document.exception

/** 저장소 업로드·조회·삭제·서명 URL 발급 실패. 클라이언트가 할 일(잠시 뒤 다시 시도)이 같아 하나로 묶는다. */
class StorageUnavailableException(action: String, objectKey: String, cause: Throwable? = null) :
    RuntimeException("Storage $action failed: $objectKey", cause)
