# 025-application-api-auth-account-integration

## 작업 개요
- 작업명: Application API auth·account 저장소 통합 연결
- 관련 TASK: PR #55 / Issue #48
- 관련 API/문서: `/api/identity/v11/applications/*`, `auth-review.local.md`
- 커밋 메시지: `fix(identity): auth account 저장소와 Application API 통합 연결 #48`

## 변경한 파일
- `systems/identity/identity-adapter-in/src/test/kotlin/.../ApplicationControllerTest.kt`
- `systems/identity/identity-adapter-out/src/main/kotlin/.../AccountApplicationDataPersistenceAdapter.kt`
- `systems/identity/identity-adapter-out/src/test/kotlin/.../JpaAccountRepositoryAdapterIntegrationTest.kt`
- `systems/identity/identity-application/src/main/kotlin/.../mock/*`, `deps.bzl`
- `systems/identity/identity-bootstrap/src/main/kotlin/.../IdentityApplicationConfig.kt`
- `systems/identity/identity-bootstrap/src/main/kotlin/.../IdentityMockAdapterConfig.kt`
- `systems/identity/identity-bootstrap/src/test/kotlin/.../IdentityApplicationHttpIntegrationTest.kt`
- `systems/identity/identity-bootstrap/src/test/kotlin/.../SecurityConfigTest.kt`
- `systems/identity/.local.docs/task/025-application-api-auth-account-integration.md`

## 구체적인 구현 내용
- 실제 `AccountRepository`와 `StudentProfile`을 application `ApplicationDataPort`에 연결했다.
- 운영 profile에서는 mock adapter 대신 account profile 기반 persistence adapter를 사용하도록 구성했다.
- 실제 JWT 인증으로 status·result·cancellation API를 호출하는 HTTP 통합 테스트를 추가했다.
- 잘못된 JWT, 결과 미발표, 취소 후 DB 재조회 상태를 검증하도록 했다.

## 검증 내용
- 실행 명령: `bazel build //systems/identity/identity-adapter-out:main //systems/identity/identity-bootstrap:main`
- 실행 명령: `bazel test //systems/identity/identity-adapter-out:test --test_output=errors`
- 실행 명령: `bazel test //systems/identity/identity-bootstrap:test --test_output=errors`
- 검증 결과: build 및 adapter-out/bootstrap test suite 통과
- 미검증 사유: Docker daemon 미탐지로 MySQL·Redis Testcontainers HTTP 통합 시나리오는 skip됨
- 미검증 사유: 저장소에 ktlint 실행 파일·설정이 없어 ktlint는 실행하지 못함

## 추후 추가해야 할 사항
- Docker 환경에서 HTTP·persistence Testcontainers 시나리오를 재실행한다.
- 수정 커밋을 PR #55에 반영하고 CodeRabbit 재리뷰를 수행한다.
