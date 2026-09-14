# 공지 수정·삭제 작업 컨텍스트

- 이슈: [#161](https://github.com/EntryDSM/entrydsm-platform/issues/161)
- 브랜치: `feature/161-notice-update-delete` (`origin/develop` 73ca391 에서 분기)
- 선행 작업: #139 / PR #140 — 공지 소유권을 notification 으로 옮기고 admin 은 gRPC `CreateNotice` 로 등록만 위임

개발 전에 현재 구조와 결정 사항을 정리한 문서다. 구현 중에 판단이 바뀌면 이 문서를 먼저 고친다.

## 1. 목표

| 기능 | admin HTTP (API 소유) | notification gRPC (데이터 소유) |
| --- | --- | --- |
| 수정 | `PATCH /api/v11/admin/notices/{noticeId}` → `204` | `UpdateNotice` |
| 삭제 | `DELETE /api/v11/admin/notices/{noticeId}` → `204` | `DeleteNotice` |

admin 은 DB 를 건드리지 않고, notification 이 `notification_db.notices` 를 바꾼다.

## 2. 현재 구조 (develop 73ca391)

### 공지 등록 흐름

```text
POST /api/v11/admin/notices
  SupportController.createNotice            (admin-adapter-in)
  → CreateNoticeUseCase = SupportService    (admin-application)
  → NoticeRepository = GrpcNoticeAdapter    (admin-adapter-out, gRPC 클라이언트)
  ── gRPC CreateNotice ──▶
  NotificationGrpcService.createNotice      (notification-adapter-in)
  → NotificationPort = NotificationService  (notification-application)
  → NoticeRepository = NoticePersistenceAdapter → notices 테이블 (notification-adapter-out)
```

### 관련 파일

| 모듈 | 파일 | 역할 |
| --- | --- | --- |
| contracts | `contracts/proto/notification.proto` | `CreateNotice`, `AnswerQuestion` 두 RPC. 수정·삭제 없음 |
| admin-adapter-in | `web/SupportController.kt` | 공지 등록·QnA 답변·내보내기 컨트롤러 |
| admin-adapter-in | `web/AdminEndpointPaths.kt` | `NOTICES = "$BASE/notices"` |
| admin-adapter-in | `web/dto/request/AdminRequests.kt` | `CreateNoticeRequest` (`title` ≤200, `division` 기본값 `ADMISSION_NOTICE`, `isPinned`, `attachmentIds`) |
| admin-adapter-in | `web/exception/GlobalExceptionHandler.kt` | `AdminException` → 오류 코드, 요청 파싱·검증 실패·`IllegalArgumentException` → 400 |
| admin-application | `SupportService.kt` | 유스케이스 구현. gRPC 만 불러 트랜잭션 없음 |
| admin-domain | `port/in/SupportUseCases.kt` | 유스케이스 인터페이스 하나에 메서드 하나 (`CreateNoticeUseCase`) |
| admin-domain | `port/out/AdminRepositories.kt` | `NoticeRepository.save(notice)` |
| admin-domain | `command/AdminCommands.kt` | `CreateNoticeCommand` |
| admin-domain | `enum/ErrorCode.kt` | `QUESTION_NOT_FOUND`(404), `NOTIFICATION_SERVICE_UNAVAILABLE`(503) 등 |
| admin-adapter-out | `grpc/GrpcNoticeAdapter.kt` | `CreateNotice` 호출. 작성자는 `"관리자"` 고정 |
| admin-adapter-out | `grpc/NotificationGrpcChannel.kt` | 채널 하나 공유, `toAdminException(notFound)` 로 gRPC 상태 → 오류 코드 |
| notification-adapter-in | `grpc/NotificationGrpcService.kt` | `IllegalArgumentException` → `INVALID_ARGUMENT`, `NotificationNotFoundException` → `NOT_FOUND`, 그 외 → `INTERNAL`("internal server error") |
| notification-application | `port/in/NotificationPort.kt`, `service/NotificationService.kt` | 조회·등록·QnA 답변 |
| notification-application | `port/in/command/CreateNoticeCommand.kt` | `init` 에서 제목(1..255)·본문·작성자 검증 |
| notification-application | `port/out/NoticeRepository.kt` | `findPage`, `findById`, `create` |
| notification-adapter-out | `repository/NoticePersistenceAdapter.kt` | 클래스 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional` |
| notification-adapter-out | `entity/NoticeJpaEntity.kt` | `title`, `content`, `category`, `author`, `view_count`, `is_pinned`, `attachment_ids`(쉼표로 이은 TEXT, 없으면 NULL), `created_at`, `updated_at` |
| notification-domain | `model/NoticeCategory.kt` | `from()` 이 열거형 이름·한글 이름·명세 영문 이름을 모두 받는다 |

같은 구조의 선례는 QnA 답변이다. `FaqRepository.answer(command): Faq?` 가 없으면 `null` 을 돌려주고, 서비스가 `NotificationNotFoundException` 으로 바꾸고, admin 은 `toAdminException(notFound = QUESTION_NOT_FOUND)` 로 404 를 만든다.

### 제약

- 공개 조회 API(`NoticeSummaryResponse`, `NoticeDetailResponse`)는 분류·고정 여부·첨부를 돌려주지 않는다. notification 도메인 `Notice` 에도 두 필드가 없다
- `notices` 에 삭제 표시·수정자 컬럼이 없다
- 내부 gRPC 는 평문이고 인증이 없다 (#151). 쓰기 멱등성도 없다 (#150)
- 게이트웨이는 GET·HEAD·OPTIONS 만 재시도한다 (`DownstreamClientPolicy.SAFE_RETRY_METHODS`)
- Notion API 명세에 수정·삭제 행이 없다. 이번 명세는 `api-spec.md` 로 남긴다

## 3. 결정

### 3.1 수정은 PATCH 부분 수정

보낸 필드만 바꾸고, 없거나 `null` 인 필드는 유지한다.

- PUT(전체 교체)으로 하면 프론트가 분류·고정 여부·첨부를 다시 보내야 하는데, 조회 API 가 그 값을 주지 않아 수정할 때마다 지워진다
- admin 기존 관례와도 맞다. 단건 정정은 PATCH(`arrival`, `status`), 조회 API 가 전체를 주는 싱글턴 교체는 PUT(`score-policy`, `admission-quotas`)
- 필드 이름과 값은 등록 요청과 같다: `title`, `content`, `division`, `isPinned`, `attachmentIds`
- `attachmentIds` 는 목록 전체 교체. `[]` 는 첨부를 모두 뗀다
- 빈 본문 `{}` 은 거절하지 않는다. 바뀌는 값 없이 `updated_at` 만 갱신된다
- 작성자(`author`)는 바꾸지 않는다. 등록 때와 같이 `"관리자"` 로 남는다

### 3.2 gRPC 계약

```proto
rpc UpdateNotice(UpdateNoticeRequest) returns (UpdateNoticeResponse);
rpc DeleteNotice(DeleteNoticeRequest) returns (DeleteNoticeResponse);

message UpdateNoticeRequest {
    int64 notice_id = 1;
    optional string title = 2;
    optional string content = 3;
    optional string category = 4;   // CreateNoticeRequest.category 와 같은 값
    optional bool is_pinned = 5;
    AttachmentIds attachment_ids = 6; // 메시지 필드라 유무를 구분할 수 있다
}

message AttachmentIds { repeated string values = 1; }
message UpdateNoticeResponse { int64 notice_id = 1; int64 updated_at_epoch_millis = 2; }
message DeleteNoticeRequest { int64 notice_id = 1; }
message DeleteNoticeResponse {}
```

- 필드 유무는 proto3 `optional` 로 넘긴다 (`application.proto`, `configuration.proto` 에서 이미 쓰고 있다)
- `repeated` 는 "안 보냄"과 "빈 목록"이 같아서 메시지로 감싼다
- 시각은 기존 규칙대로 UTC epoch millis

### 3.3 삭제는 hard delete

- 삭제 표시 컬럼이 없고, 조회 쿼리가 걸러내지도 않는다. soft delete 는 마이그레이션과 조회 경로 수정까지 번진다
- 첨부 파일(configuration)은 지우지 않는다

### 3.4 응답과 오류

- 수정·삭제 모두 `204 No Content`. admin 의 PATCH 선례(`arrival`, `status`)와 같다. 응답 본문이 필요해지면 notification `Notice` 에 고정 여부·첨부를 넣어야 해서 이번에는 하지 않는다

| 상황 | notification gRPC | admin HTTP |
| --- | --- | --- |
| 없는 공지 | `NOT_FOUND` | 404 `NOTICE_NOT_FOUND` (신규) |
| 잘못된 분류, 빈 제목·본문, 제목 255자 초과 | `INVALID_ARGUMENT` | 400 `INVALID_REQUEST_BODY` |
| 제목 200자 초과, 본문 파싱 실패, `noticeId` 형식 오류 | (호출 안 함) | 400 `INVALID_REQUEST_BODY` |
| notification 연결 실패·타임아웃 | `UNAVAILABLE`, `DEADLINE_EXCEEDED` | 503 `NOTIFICATION_SERVICE_UNAVAILABLE` |
| 그 밖의 실패 (새 RPC 가 없는 구버전 notification 의 `UNIMPLEMENTED` 포함) | `INTERNAL` 등 | 500 `INTERNAL_SERVER_ERROR` |

### 3.5 검증 위치

- 규칙의 주인은 notification 이다. `UpdateNoticeCommand.init` 에서 값이 있는 필드만 검증한다 (제목 1..255, 본문 공백 불가). 분류는 `NoticeCategory.from()`
- admin DTO 는 등록과 같은 상한(`title` ≤200)만 건다. `null` 허용 필드의 공백 검사는 notification 에 맡긴다

## 4. 변경 계획

### contracts

- `contracts/proto/notification.proto`: 3.2 의 RPC 두 개와 메시지 추가. BUILD 변경 없음

### notification

| 파일 | 변경 |
| --- | --- |
| `port/in/command/UpdateNoticeCommand.kt` | 신규. `noticeId`, nullable `title`/`content`/`category`/`isPinned`/`attachmentIds` |
| `port/in/NotificationPort.kt` | `updateNotice(command): NoticeDetailResult`, `deleteNotice(id: Long)` |
| `port/out/NoticeRepository.kt` | `update(command): Notice?`, `deleteById(id): Boolean` |
| `service/NotificationService.kt` | 없으면 `NotificationNotFoundException("notice not found: id=…")` |
| `repository/NoticePersistenceAdapter.kt` | `update`: 조회 → 값 있는 필드만 대입 → `updatedAt = now` → 저장. `deleteById`: 조회 → 삭제. 첨부 직렬화는 `create` 와 같은 함수 사용 |
| `grpc/NotificationGrpcService.kt` | `updateNotice`: `hasXxx()` 로 유무 판별. `deleteNotice` |
| 테스트 | `NotificationServiceTest`(서비스), `NotificationAdapterInModuleTest`(gRPC 필드 유무·상태 매핑) |

### admin

| 파일 | 변경 |
| --- | --- |
| `command/AdminCommands.kt` | `UpdateNoticeCommand` (`division` 등 nullable) |
| `enum/ErrorCode.kt` | `NOTICE_NOT_FOUND(404, "공지를 찾을 수 없습니다.")` |
| `port/in/SupportUseCases.kt` | `UpdateNoticeUseCase.update(command)`, `DeleteNoticeUseCase.delete(noticeId)` |
| `port/out/AdminRepositories.kt` | `NoticeRepository.update(command)`, `deleteById(noticeId)` |
| `SupportService.kt` | 두 유스케이스 구현 (저장소 위임) |
| `grpc/GrpcNoticeAdapter.kt` | 값 있는 필드만 요청에 채움. `NOT_FOUND` → `NOTICE_NOT_FOUND` |
| `web/AdminEndpointPaths.kt` | `NOTICE = "$NOTICES/{noticeId}"` |
| `web/dto/request/AdminRequests.kt` | `UpdateNoticeRequest` (모두 nullable, `isPinned` 는 `@JsonProperty`) |
| `web/SupportController.kt` | `@PatchMapping`, `@DeleteMapping` → 204 |
| 테스트 | `AdminAdapterOutModuleTest`: 로컬 포트에 가짜 notification gRPC 서버를 띄워 요청 필드 유무와 `NOT_FOUND` 매핑 확인 |

인증은 기존 `AdminAuthorizationInterceptor` 가 `/api/v11/admin/**` 전체에 걸려 있어 추가 작업이 없다.

## 5. 커밋 순서

각 커밋은 단독으로 빌드돼야 한다. 형식은 `type(scope): 요약 #161` (scope 는 `.commitlintrc.cjs` 의 `scope-enum`).

1. `docs(documents)`: 이 문서
2. `feat(contracts)`: `UpdateNotice`, `DeleteNotice` 계약
3. `feat(notification)`: 수정·삭제 유스케이스와 저장 경로 + 서비스 테스트
4. `feat(notification)`: gRPC 서버 + adapter-in 테스트
5. `feat(admin)`: 유스케이스·gRPC 클라이언트·HTTP API + adapter-out 테스트
6. `docs(documents)`: API 명세 (`api-spec.md`)

## 6. 검증

```bash
bazel build //contracts:all //systems/admin/... //systems/notification/...
bazel test //systems/admin/... //systems/notification/...
```

로컬 E2E (검증용 MySQL 은 3307 컨테이너, 서비스는 `bazel-bin/.../main` 직접 실행)

1. notification 기동: Flyway V028~V030, 스키마 검증, gRPC 리슨
2. admin `POST` 로 공지 등록 → `PATCH` 로 제목만 수정 → DB 에서 나머지 컬럼(분류·고정·첨부) 유지 확인
3. `attachmentIds: []` → `attachment_ids` NULL, `division` 변경 → 목록 `?category=` 필터 반영
4. `DELETE` → 204, 상세 조회 404, 다시 `DELETE` → 404 `NOTICE_NOT_FOUND`
5. 없는 id `PATCH` → 404, 잘못된 분류 → 400, notification 중지 → 503

## 7. 범위 밖

- 조회 API 에 분류·고정 여부·첨부 노출, 고정 공지 우선 정렬
- 첨부 파일 정리, 수정자·이력 기록
- gRPC 멱등성(#150), 내부 gRPC 인증(#151)
- Notion API 명세 DB 반영 (병합 후)
