package hs.kr.entrydsm.application.domain

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 지금 시각입니다. 저장하는 시각은 모두 UTC 입니다.
 *
 * 시각을 `LocalDateTime` 으로 보관하지만 gRPC 와 이벤트는 `ZoneOffset.UTC` 로 epoch 를 만듭니다.
 * `LocalDateTime.now()` 로 쓰면 JVM 기본 시간대를 타서, 컨테이너(UTC) 밖에서 돌릴 때 읽는 쪽과
 * 어긋납니다. 시각을 쓰는 곳은 모두 이 함수를 지납니다.
 */
fun nowUtc(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
