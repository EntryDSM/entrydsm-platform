# Kotlin Convention

이 문서는 EntryDSM Platform Kotlin 코드 작성 규칙을 정의합니다.

## Index

1. [기본 원칙](#1-기본-원칙)
2. [패키지 구조와 의존성 방향](#2-패키지-구조와-의존성-방향)
3. [Naming](#3-naming)
4. [DTO](#4-dto)
5. [Command / Result](#5-command--result)
6. [Domain](#6-domain)
7. [Exception](#7-exception)
8. [Spring](#8-spring)
9. [Nullability](#9-nullability)
10. [Formatting](#10-formatting)
11. [Test](#11-test)
12. [Bazel](#12-bazel)

## 1. 기본 원칙

- Kotlin 공식 코딩 컨벤션을 기본으로 따릅니다.
- 기존 모듈의 패키지 구조, 네이밍, 의존성 방향을 우선합니다.
- 코드 스타일보다 아키텍처 경계를 먼저 지킵니다.
- 불필요한 추상화보다 명확한 도메인 표현을 우선합니다.

## 2. 패키지 구조 & 의존성 방향

### Hexagonal Architecture

서비스는 멀티모듈과 헥사고날 아키텍처를 기준으로 구성합니다.

```text
systems/{service}/{service}-adapter-in
systems/{service}/{service}-adapter-out
systems/{service}/{service}-application
systems/{service}/{service}-domain
systems/{service}/{service}-bootstrap
```

#### 역할

| Module | 역할 |
| --- | --- |
| `adapter-in` | HTTP, gRPC 등 외부 요청 진입점 |
| `adapter-out` | DB, 외부 API, 메시징 등 외부 시스템 연동 |
| `application` | 유스케이스, 서비스, 포트 조합 |
| `domain` | 순수 도메인 모델, 도메인 규칙 |
| `bootstrap` | Spring Boot 실행 및 설정 조립 |

#### 의존성 방향

```text
adapter-in -> application -> domain
adapter-out -> application/domain
bootstrap -> adapter-in, adapter-out, application, domain
```

- Controller는 Service 구현체가 아니라 Port 인터페이스에 의존합니다.
- domain 모듈은 Spring, JPA, Web 등 프레임워크에 의존하지 않습니다.
- application 모듈은 유스케이스 흐름을 담당하고 외부 시스템 구현 세부사항을 알지 않습니다.
- adapter-out은 application/domain의 outbound Port를 구현합니다.

### Layered Architecture

기본 아키텍처는 헥사고날 아키텍처입니다. 다만 일부 서비스는 복잡도나 기존 구현 방식에 따라 레이어드 아키텍처를 사용할 수 있습니다.

레이어드 아키텍처를 사용하는 경우 다음 구조를 따릅니다.

```text
controller -> service -> repository
```

#### 역할

| Layer | 역할 |
| --- | --- |
| `controller` | HTTP 요청/응답 처리, DTO 변환 |
| `service` | 유스케이스 흐름, 트랜잭션 경계, 도메인 규칙 조합 |
| `repository` | 데이터 저장소 접근 |
| `domain` | 엔티티, 값 객체, 도메인 상태 및 규칙 |

#### 규칙

- Controller는 Repository에 직접 의존하지 않습니다.
- Controller는 Service만 호출합니다.
- Service는 트랜잭션 경계를 담당합니다.
- Repository는 데이터 접근만 담당하고 비즈니스 규칙을 포함하지 않습니다.
- DTO는 Controller 계층에 두고, Entity를 API 응답으로 직접 노출하지 않습니다.
- 서비스가 헥사고날인지 레이어드인지 모듈 구조와 기존 패턴을 먼저 확인한 뒤 동일한 방식을 따릅니다.

## 3. Naming

### Class

| 대상 | 형식 | 예시 |
| --- | --- | --- |
| Controller | `{Domain}Controller` | `AuthController` |
| Service | `{UseCase}Service` | `EnvironmentVariableService` |
| Inbound Port | `{Action}{Domain}UseCase` 또는 `{Domain}Port` | `CreateAccountUseCase`, `AuthPort` |
| Outbound Port | `{Domain}Repository`, `{External}Port` | `AccountRepository` |
| Adapter | `{Domain}{Role}Adapter` | `AccountPersistenceAdapter` |
| Request DTO | `{Action}Request` | `LoginRequest` |
| Response DTO | `{Domain}Response` | `AccountResponse` |
| Command | `{Action}Command` | `SignupCommand` |
| Result | `{Domain}Result` | `UserSummaryResult` |
| Exception | `{Reason}Exception` | `UserNotFoundException` |

### Function

- 동사는 행위를 명확히 표현합니다.
- 조회는 `find`, `get`, `read` 중 의미에 맞게 사용합니다.
- 상태 변경은 `create`, `update`, `delete`, `cancel`, `submit` 등 도메인 행위로 표현합니다.

```kotlin
fun login(command: LoginCommand): UserSummaryResult

fun cancelApplication(command: CancelApplicationCommand): ApplicationStatusResult
```

## 4. DTO

- DTO는 adapter 계층에 둡니다.
- API 요청/응답 형식과 application 내부 모델을 분리합니다.
- DTO에서 도메인 규칙을 처리하지 않습니다.
- DTO 변환 함수는 단순 매핑만 수행합니다.

### 이름 형식

| 대상 | 형식 | 예시 |
| --- | --- | --- |
| 요청 DTO | `{Action}{Domain}Request` | `LoginRequest`, `SignupRequest`, `CancelApplicationRequest` |
| 응답 DTO | `{Domain}Response` 또는 `{Action}{Domain}Response` | `AccountResponse`, `ApplicationStatusResponse`, `LoginResponse` |
| 목록 응답 DTO | `{Domain}ListResponse` | `AccountListResponse` |
| 상세 응답 DTO | `{Domain}DetailResponse` | `AccountDetailResponse` |
| 공통 응답 DTO | `{Purpose}Response` | `ApiResponse`, `ErrorResponse` |

단일 API에서만 사용하는 DTO는 API 행위가 드러나도록 작성합니다.

```kotlin
data class LoginRequest(
    val loginId: String,
    val password: String,
)

data class LoginResponse(
    val userId: String,
    val role: String,
    val status: AccountStatus,
)
```

도메인 조회처럼 여러 API에서 재사용 가능한 응답은 도메인 중심으로 작성합니다.

```kotlin
data class AccountResponse(
    val userId: String,
    val role: String,
    val status: AccountStatus,
)

data class ApplicationStatusResponse(
    val applicantStatus: ApplicantStatus,
    val submittedAt: Instant?,
    val updatedAt: Instant,
)
```

응답은 공통 envelope을 사용합니다.

```kotlin
data class ApiResponse<T>(
    val success: Boolean = true,
    val data: T?,
    val error: Any? = null,
)
```

## 5. Command / Result

- Controller에서 받은 요청은 application Port 호출 전에 Command로 변환합니다.
- application 계층은 Request/Response DTO를 직접 알지 않습니다.
- 조회 결과는 Result 모델로 반환하고 adapter-in에서 Response DTO로 변환합니다.

```kotlin
data class LoginCommand(
    val loginId: String,
    val password: String,
)
```

## 6. Domain

- domain 모델은 프레임워크 의존성을 갖지 않습니다.
- 도메인 상태는 enum 또는 value object로 명확히 표현합니다.
- 생성/변경 규칙은 가능한 도메인 내부에 둡니다.
- 단순 데이터 전달만 필요한 경우에도 도메인 의미가 드러나는 이름을 사용합니다.

## 7. Exception

- 예외는 도메인 또는 application 의미를 드러내는 이름으로 작성합니다.
- API 에러 응답은 ErrorCode와 Exception Handler에서 일관되게 변환합니다.
- Controller에서 직접 에러 응답을 조립하지 않습니다.

```kotlin
class UserNotFoundException(
    userId: String,
) : RuntimeException("User not found: $userId")
```

## 8. Spring

- `@RestController`, `@RequestMapping` 등 Web annotation은 adapter-in에서만 사용합니다.
- `@Component`, `@Service`, `@Transactional`은 application 또는 adapter 구현체에만 사용합니다.
- domain에는 Spring annotation을 사용하지 않습니다.
- 트랜잭션은 use case 경계에서 선언합니다.

```kotlin
@Service
@Transactional(readOnly = true)
class AccountService(
    private val accountRepository: AccountRepository,
) : ReadAccountUseCase
```

## 9. Nullability

- nullable 타입은 실제로 값이 없을 수 있는 경우에만 사용합니다.
- nullable 값을 받은 직후 의미 있는 기본값 또는 예외로 처리합니다.
- `!!` 사용은 금지합니다.

```kotlin
val authorization: String?
```

## 10. Formatting

- 한 줄이 길어지면 trailing comma를 사용해 여러 줄로 나눕니다.
- 생성자 파라미터가 2개 이상이면 여러 줄로 작성합니다.
- import wildcard는 사용하지 않습니다.
- 의미 없는 주석은 작성하지 않습니다.

```kotlin
data class AccountResult(
    val userId: String,
    val role: String,
    val status: AccountStatus,
)
```

## 11. Test

- Controller 테스트는 요청/응답 status, body, header를 검증합니다.
- Service 테스트는 유스케이스 흐름과 예외를 검증합니다.
- domain 테스트는 도메인 규칙이 생긴 시점에 추가합니다.
- 테스트 이름은 어떤 조건에서 어떤 결과가 나오는지 드러나게 작성합니다.

## 12. Bazel

- 새 Kotlin 파일을 추가할 때는 해당 모듈 `BUILD.bazel`의 `glob(["src/main/kotlin/**/*.kt"])` 범위에 들어가야 합니다.
- 외부 라이브러리를 사용하는 경우 해당 모듈의 `deps.bzl`에 직접 의존성을 추가합니다.
- transitive dependency에 기대어 컴파일하지 않습니다.

```python
KOTLIN_DEPS = [
    "@maven//:org_springframework_spring_tx",
    "//systems/configuration/configuration-domain:main",
]
```
