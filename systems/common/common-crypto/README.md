# 공통 암호화

`//systems/common/common-crypto:main`을 참조하는 JVM 라이브러리다. 별도 서버나 네트워크 호출 없이 각 서비스 프로세스에서 실행한다. 패키지는 `hs.kr.entrydsm.common.crypto`이며 JDK만 사용한다.

작업 범위는 **공통 모듈 개발 및 기존 서비스가 암호화/복호화 시 해당 모듈을 사용하도록 변경**이다. AES-GCM 연산은 내부 `AesGcm`으로 공유하고, 개인정보 문자열은 `PersonalDataCipher`, 원서 스냅샷은 `SnapshotCipher`가 담당한다.

## identity/application 개인정보

`PersonalDataCipher`는 기존 `v1.<Base64URL nonce>.<Base64URL 암호문+태그>` 형식과 12바이트 nonce·128비트 인증 태그를 유지한다. 새로 쓰는 암호문도 기존 코드에서 읽을 수 있어 개인정보 데이터 마이그레이션은 필요하지 않다.

- identity의 `AesGcmPersonalDataEncryptor`는 이 모듈에 위임한다. 계정 개인정보 및 Redis PASS 증명 암호화가 함께 전환되며 평문은 계속 거부한다.
- application의 `PersonalDataConverter`도 같은 모듈에 위임한다. 기존 null 처리와 이전 평문 컬럼 읽기를 유지한다. 공통 모듈 자체는 평문을 복호화 결과로 대신 반환하지 않는다.
- identity는 기존 `IDENTITY_PII_ENCRYPTION_KEY_BASE64`, application은 기존 `APPLICATION_PII_ENCRYPTION_KEY_BASE64`를 사용한다. 키를 변경하거나 서로 공유할 필요가 없다. 아래 키 ID 기반 교체 기능은 스냅샷에 적용되며, 기존 개인정보 키 교체에는 별도 데이터 재암호화가 필요하다.
- BCrypt 비밀번호 해시, HMAC, JWT 서명은 가역 암호화 대상이 아니므로 기존 구현을 유지한다.

## application/admin 스냅샷

```java
SnapshotCipher cipher = new SnapshotCipher("new", Map.of("new", currentKey, "old", previousKey));
byte[] encrypted = cipher.encrypt(plaintext);
byte[] decrypted = cipher.decrypt(encrypted);
```

키는 Base64로 인코딩한 128/192/256비트 AES 키다. 키 ID는 영문·숫자·`_`·`-`로 구성한 1~64자이며, Spring 설정에서는 소문자 ID를 사용한다. 암호문은 버전 2, 키 ID 길이와 ID, 무작위 12바이트 nonce, 암호문과 16바이트 인증 태그로 구성한다. 버전과 키 ID도 AES-GCM의 인증 대상이다. 지원하지 않는 형식·알 수 없는 키·인증 실패는 거부한다.

## application/admin 설정

각 서비스 bootstrap의 `SnapshotCipherConfiguration`이 아래 설정을 읽어 빈을 생성한다. 잘못된 키 설정은 시작 시 실패한다.

- `APPLICATION_SNAPSHOT_KEY_ID`: 현재 암호화 키 ID.
- `APPLICATION_SNAPSHOT_KEY_BASE64`: 현재 스냅샷 전용 키. 원본 `APPLICATION_PII_ENCRYPTION_KEY_BASE64`와 다른 키를 사용한다.

이전 버전 2 키는 Spring의 `security.snapshot.previous-keys` 맵으로 설정한다. 키 ID를 다른 키 재료에 재사용하거나 현재 ID를 이 맵에 중복 등록하지 않는다.

```yaml
security:
  snapshot:
    current-key-id: ${APPLICATION_SNAPSHOT_KEY_ID}
    current-key-base64: ${APPLICATION_SNAPSHOT_KEY_BASE64}
    previous-keys:
      snapshot-2026-09: ${APPLICATION_SNAPSHOT_PREVIOUS_KEY_BASE64}
```

같은 스냅샷을 다루는 application/admin은 읽어야 하는 키 ID와 키 재료를 공유한다. 다른 서비스가 라이브러리를 사용한다고 같은 키를 제공할 필요는 없다. 키 설정은 환경변수·배포 Secret 등 서비스 설정에서 주입하고 저장소에는 실제 키를 기록하지 않는다.

## 스냅샷 배포와 키 교체

1. application/admin에 동일한 스냅샷 키 ID·키를 설정하고 두 서비스 모두 버전 2 스냅샷을 사용하는 코드로 전환한다.
2. **버전 1 스냅샷은 읽지 않는다.** 기존 버전 1 이벤트·projection이 있는 환경은 별도 정리·버전 2 재적재 계획이 필요하다. 이 변경은 기존 DB/Redis 데이터를 자동으로 삭제하거나 마이그레이션하지 않는다. identity/application의 `v1.` 개인정보 문자열 형식과는 별개다.
3. 버전 2 키 교체는 모든 소비 서비스에 새 키를 읽을 수 있도록 먼저 배포한 뒤 현재 키 ID를 변경한다. 이전 버전 2 키는 `previous-keys`에 유지한다.
4. 이전 키로 암호화된 모든 outbox·Redis 이벤트·실패 복구 대상·admin projection이 정리되거나 새 키로 재암호화된 것을 확인한 뒤 이전 키를 제거한다. admin의 활성 projection은 TTL로 만료되지 않고 같은 원서 버전의 대사는 payload를 갱신하지 않으므로, 이벤트 보관 기간 7일만 기다려서는 키를 제거할 수 없다. 자동 일괄 재암호화 작업은 이 모듈에 포함하지 않는다.

## 검증

`bazel test //systems/common/common-crypto:test`로 두 암호문 형식의 왕복 복호화, nonce 생성, 버전 2 스냅샷 키 교체, 버전 1 스냅샷 거부, 개인정보 문자열의 기존 형식 양방향 호환성, 잘못된 키와 암호문 변조를 검증한다. 각 서비스의 `snapshot_cipher_configuration_test`는 Spring 설정 바인딩과 잘못된 설정의 시작 실패를 검증한다. identity 전체 테스트와 application 개인정보 저장 테스트도 실행한다. Docker가 없으면 기존 identity MySQL·Redis·HTTP 통합 테스트는 조건부로 생략되므로 해당 환경의 검증 결과와 구분한다.
