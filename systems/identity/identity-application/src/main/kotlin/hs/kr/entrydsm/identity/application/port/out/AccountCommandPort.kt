package hs.kr.entrydsm.identity.application.port.out

import hs.kr.entrydsm.identity.domain.model.Account
import java.time.Instant
import hs.kr.entrydsm.identity.domain.model.PasswordHash

/** CQRS command port for account writes. */
interface AccountCommandPort {
    fun save(account: Account): Account

    /** 비밀번호 변경과 전체 토큰 폐기를 하나의 트랜잭션으로 처리한다. */
    fun changePasswordAndRevoke(userId: Long, expectedPasswordHash: PasswordHash, newPasswordHash: PasswordHash)

    fun register(registration: AccountRegistration, createdAt: Instant): Account
}
