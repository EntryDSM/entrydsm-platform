package hs.kr.entrydsm.admin.domain.port.out

/** 전형 진행 기록을 정정 트랜잭션 안에서 잠근다. */
fun interface ApplicationCorrectionStatePort {
    fun lock(applicantId: Long): ApplicationCorrectionState
}

data class ApplicationCorrectionState(val hasResult: Boolean)
