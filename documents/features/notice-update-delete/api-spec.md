# 공지 수정·삭제 API 명세

> **갱신 필요**: 모듈러 모놀리식 전환으로 admin → notification 호출이 gRPC 에서 in-process 모듈 API(`notification-api` 의 `NotificationApi`)로 바뀌었다.
> 요청 필드의 유무 구분(null 유지 vs 빈 목록)과 오류 매핑(`INVALID_ARGUMENT`→400, `NOT_FOUND`→`NOTICE_NOT_FOUND`)은 그대로다.
> 아래 gRPC 설명은 전환 전 기준이다. 자세한 내용은 [모듈러 모놀리식 전환 계획](../../modular-monolith/plan.md) 참고.

- 이슈: [#161](https://github.com/EntryDSM/entrydsm-platform/issues/161)
- 설계 배경: [context.md](./context.md)
- API 소유: admin. 공지 데이터는 notification 이 소유하고, admin 이 gRPC 로 변경을 위임한다

| 기능 | Method | End point | 인증 | 성공 |
| --- | --- | --- | --- | --- |
| 공지 수정 | `PATCH` | `/api/v11/admin/notices/{noticeId}` | O (ADMIN) | `204 No Content` |
| 공지 삭제 | `DELETE` | `/api/v11/admin/notices/{noticeId}` | O (ADMIN) | `204 No Content` |

## 공통

### 인증

게이트웨이가 토큰을 확인하고 `X-User-Id`, `X-User-Role` 헤더를 넣어 admin 으로 넘긴다. admin 은 두 헤더가 없으면 401, 역할이 `ADMIN` 이 아니면 403 을 돌려준다.

### Path Variable

| 이름 | 타입 | 설명 |
| --- | --- | --- |
| `noticeId` | long | 공지 식별자. 등록 응답의 `noticeId`, 공지 목록·상세 조회의 `noticeId` 와 같다 |

### 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "NOTICE_NOT_FOUND",
    "message": "공지를 찾을 수 없습니다.",
    "status": 404
  },
  "timestamp": "2026-09-14T12:58:04.432381Z"
}
```

## 1. 공지 수정

`PATCH /api/v11/admin/notices/{noticeId}`

보낸 필드만 바꾸고, 보내지 않았거나 `null` 인 필드는 기존 값을 유지한다. 필드 이름과 값은 공지 등록(`POST /api/v11/admin/notices`) 요청과 같다.

### REQUEST

```json
{
  "title": "2027학년도 신입생 모집 요강 안내 (수정)",
  "content": "...",
  "division": "PROSPECTIVE_STUDENT",
  "isPinned": false,
  "attachmentIds": ["doc_01H..."]
}
```

| 필드 | 타입 | 필수 | 규칙 |
| --- | --- | --- | --- |
| `title` | string | X | 공백만으로는 불가, 최대 200자 |
| `content` | string | X | 공백만으로는 불가 |
| `division` | string | X | 공지 분류. 아래 표의 값 중 하나 |
| `isPinned` | boolean | X | 상단 고정 여부 |
| `attachmentIds` | string[] | X | 첨부 문서 식별자 목록. 보내면 목록 전체를 교체한다 |

보낸 값에 따른 동작

| 보낸 값 | 동작 |
| --- | --- |
| 필드 없음 또는 `null` | 기존 값 유지 |
| 값 있음 | 그 값으로 교체 |
| `"isPinned": false` | 고정 해제 (값이 있는 것으로 본다) |
| `"attachmentIds": []` | 첨부를 모두 뗀다 |
| 빈 객체 `{}` | 바뀌는 값 없이 수정 시각(`updatedAt`)만 갱신 |

`division` 값

| 저장 값 | 함께 받는 이름 |
| --- | --- |
| `ADMISSION_NOTICE` | `입학 공지사항`, `Admissions Notice` |
| `PROSPECTIVE_STUDENT` | `예비 신입생 안내`, `Prospective Students Notice` |

- 앞뒤 공백은 무시한다. 저장 값과 영문 이름은 대소문자를 구분하지 않는다
- 공지 목록 조회의 `?category=` 값과 같다

작성자(`author`)는 수정할 수 없다. 등록 때와 같이 `관리자` 로 남는다.

### RESPONSE `204 No Content`

본문 없음. 변경은 notification 공지 조회에 바로 반영된다.

- 제목·본문·수정 시각: 목록·상세 조회 (`GET /api/notification/v11/notifications/notification`, `/{id}`)
- 분류: 목록 조회의 `?category=` 필터
- 고정 여부·첨부: 지금 조회 API 는 돌려주지 않는다

### 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST_BODY` | 제목이 공백이거나 200자 초과, 본문이 공백, 알 수 없는 분류, JSON 형식·타입 오류, `noticeId` 가 숫자가 아님 |
| 401 | `AUTH_UNAUTHORIZED` | 인증 헤더 없음 |
| 403 | `ACCESS_DENIED` | 관리자 권한 아님 |
| 404 | `NOTICE_NOT_FOUND` | 공지가 없음 |
| 500 | `INTERNAL_SERVER_ERROR` | notification 내부 오류 |
| 503 | `NOTIFICATION_SERVICE_UNAVAILABLE` | notification 연결 실패 또는 응답 시간 초과(기본 3초) |

## 2. 공지 삭제

`DELETE /api/v11/admin/notices/{noticeId}`

공지를 지운다(복구 불가). 첨부 문서는 파일관리 시스템에 그대로 남는다.

### REQUEST

본문 없음.

### RESPONSE `204 No Content`

본문 없음. 지운 공지는 목록에서 빠지고 상세 조회는 404 가 된다.

### 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST_BODY` | `noticeId` 가 숫자가 아님 |
| 401 | `AUTH_UNAUTHORIZED` | 인증 헤더 없음 |
| 403 | `ACCESS_DENIED` | 관리자 권한 아님 |
| 404 | `NOTICE_NOT_FOUND` | 공지가 없음 (이미 지운 공지 포함) |
| 500 | `INTERNAL_SERVER_ERROR` | notification 내부 오류 |
| 503 | `NOTIFICATION_SERVICE_UNAVAILABLE` | notification 연결 실패 또는 응답 시간 초과(기본 3초) |

503 은 notification 에서 삭제가 이미 끝났을 수도 있다. 다시 요청해서 404 가 나오면 삭제된 것이다.

## 3. 내부 gRPC 계약 (admin → notification)

`contracts/proto/notification.proto` 의 `NotificationService`

```proto
rpc UpdateNotice(UpdateNoticeRequest) returns (UpdateNoticeResponse);
rpc DeleteNotice(DeleteNoticeRequest) returns (DeleteNoticeResponse);

// 값이 있는 필드만 바꾸고, 없는 필드는 기존 값을 유지한다.
message UpdateNoticeRequest {
    int64 notice_id = 1;
    optional string title = 2;
    optional string content = 3;
    // CreateNoticeRequest.category 와 같은 이름을 받는다.
    optional string category = 4;
    optional bool is_pinned = 5;
    // 있으면 목록 전체로 교체한다. 빈 목록이면 첨부를 모두 뗀다.
    AttachmentIds attachment_ids = 6;
}

// repeated 필드는 보내지 않은 것과 빈 목록을 구분할 수 없어 메시지로 감싼다.
message AttachmentIds {
    repeated string values = 1;
}

message UpdateNoticeResponse {
    int64 notice_id = 1;
    int64 updated_at_epoch_millis = 2;
}

message DeleteNoticeRequest {
    int64 notice_id = 1;
}

message DeleteNoticeResponse {}
```

- HTTP 필드와 대응: `division` → `category`, `isPinned` → `is_pinned`, `attachmentIds` → `attachment_ids.values`
- 필드 유무는 `hasTitle()` 등으로 판별한다. admin 은 `null` 인 필드를 요청에 넣지 않는다
- `updated_at_epoch_millis` 는 UTC 기준 epoch millis 다

| gRPC 상태 | 상황 | admin 변환 |
| --- | --- | --- |
| `OK` | 성공 | `204` |
| `INVALID_ARGUMENT` | 빈 제목·본문, 제목 255자 초과, 알 수 없는 분류 | 400 `INVALID_REQUEST_BODY` |
| `NOT_FOUND` | 공지가 없음 | 404 `NOTICE_NOT_FOUND` |
| `UNAVAILABLE`, `DEADLINE_EXCEEDED` | 연결 실패, deadline(`NOTIFICATION_GRPC_DEADLINE_MS`, 기본 3000ms) 초과 | 503 `NOTIFICATION_SERVICE_UNAVAILABLE` |
| `INTERNAL` 등 그 밖 | 저장소 오류 등. 설명은 `internal server error` 로 고정 | 500 `INTERNAL_SERVER_ERROR` |
