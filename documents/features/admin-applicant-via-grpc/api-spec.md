# admin 지원자 API 명세 (application gRPC 조회로 전환)

- 이슈: 미정
- API 소유: admin. 원서 내용은 application 이 갖고, admin 은 전형 진행(수험 번호·원본 도착·전형 상태)만 갖는다
- 경로와 HTTP 규약은 그대로다. **필드만 바뀐다.**

| 순서 | 기능 | Method | End point | 인증 | 성공 |
| --- | --- | --- | --- | --- | --- |
| 1 | 지원자 목록 | `GET` | `/api/v11/admin/applicants` | O (ADMIN) | `200 OK` |
| 2 | 지원자 상세 | `GET` | `/api/v11/admin/applicants/{applicantId}` | O (ADMIN) | `200 OK` |
| 3 | 원본 도착 표시 | `PATCH` | `/api/v11/admin/applicants/{applicantId}/arrival` | O (ADMIN) | `204 No Content` |
| 4 | 전형 상태 정정 | `PATCH` | `/api/v11/admin/applicants/{applicantId}/status` | O (ADMIN) | `204 No Content` |
| 5 | 수험 번호 일괄 발급 | `POST` | `/api/v11/admin/examinee-numbers/issue` | O (ADMIN) | `200 OK` |
| 6 | 성적 정책 교체 | `PATCH` | `/api/v11/admin/score-policy` | O (ADMIN) | `204 No Content` |

## 이전과 달라진 점

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 데이터 출처 | `admin_db.applicant` (**넣는 코드가 없어 항상 0행**) | application gRPC `ListApplicants`/`GetApplicant` + admin `screening` |
| `receiptNumber` | 별도 접수 번호 | **삭제**. `applicantId` 가 접수 순서를 겸한다 |
| `isSubmitted` | 원서 원본(우편) 도착 여부 | `isArrived` 로 이름을 바꿈. 뜻은 같다 |
| `submittedAt` | 원본이 도착한 시각 | **원서를 제출한 시각**(application) |
| `arrivedAt` | 없음 | **추가**. 이전 `submittedAt` 의 뜻 |
| `name`·`birthDate`·`phoneNumber`·`region`·`admissionType`·`graduationStatus`·`schoolName` | 항상 값이 있음 | **nullable**. 제출된 원서에도 비어 있을 수 있다 |
| `score` | `subjectScore`·`attendanceScore`·`volunteerScore`·`totalScore` | `totalScore` 하나. 나머지는 만드는 코드가 없어 늘 0 이었다 |
| `PATCH /score-policy` 의 `recalculate` | 지원자 총점 재계산 | **삭제**. 총점의 주인은 application 하나다 |
| 목록 정렬 | 접수 번호 오름차순 | `applicantId` 오름차순 |
| 통계 `DAILY_TREND` | 원본 도착일 기준 | **원서 제출일** 기준 |
| 통계 `REGION_DISTRIBUTION`·`TYPE_DISTRIBUTION` | 전원 집계 | 지역·전형이 빈 원서는 빠진다. `APPLICANT_COUNT.total` 에는 든다 |
| 엑셀 열 | 15열 | 12열 ("교과 점수"·"출결 점수"·"봉사 점수" 삭제, "접수번호" 값은 `applicantId` 를 네 자리로 채운 `0001` 꼴) |
| 수험표 내보내기 파일 | ZIP 안에 지원자별 `admission_ticket_{receiptNumber}.pdf` | 1차 합격자 수험표를 수험번호 순으로 한 시트에 이어 그린 xlsx 하나(지난해 `수험표.xlsx` 양식). 객체 키 `admission-ticket/admission_tickets_{exportJobId}.xlsx`, 내보내기 완료 응답의 `downloadUrl` 로 받는다 (#254, `documents/features/admission-ticket-print`) |
| application 장애 | 해당 없음 | `503 APPLICATION_SERVICE_UNAVAILABLE` |

## 공통

### 인증

게이트웨이가 토큰을 확인하고 `X-User-Id`, `X-User-Role` 헤더를 넣어 admin 으로 넘긴다. admin 은 두 헤더가 없으면 401, 역할이 `ADMIN` 이 아니면 403 을 돌려준다.

### 빈 값

원서 제출 검증이 요구하는 값은 전형·이름·보호자·자기소개·학업계획뿐이다. **제출된 원서에도 지역·학력·생년월일·연락처·중학교·총점이 비어 있을 수 있다.** 목록·상세·엑셀은 이를 `null`(엑셀은 빈 칸)로 내려주고 500 을 내지 않는다. 전형 산출에서는 지역이나 전형이 비어 묶을 수 없는 지원자를 `excluded` 로 뺀다.

### 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "APPLICATION_SERVICE_UNAVAILABLE",
    "message": "원서 서비스를 일시적으로 사용할 수 없습니다.",
    "status": 503
  },
  "timestamp": "2026-09-18T13:38:16.659838Z"
}
```

| 코드 | 상태 | 언제 |
| --- | --- | --- |
| `APPLICANT_NOT_FOUND` | 404 | 그 `applicantId` 의 원서가 없다 |
| `INVALID_STATUS_TRANSITION` | 409 | 정상 흐름을 벗어난 상태 전이 (강제 변경이 아닐 때) |
| `APPLICATION_SERVICE_UNAVAILABLE` | 503 | application 이 응답하지 않거나 제한 시간을 넘겼다 |

공지·QnA·내보내기 목록은 application 을 부르지 않으므로 application 이 죽어도 200 이다.

## 1. 지원자 목록

`GET /api/v11/admin/applicants`

### REQUEST

| 파라미터 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `keyword` | string | X | 이름(application) 또는 수험 번호(admin) 부분 일치 |
| `regions` | `DAEJEON`\|`NATIONWIDE` 목록 | X | |
| `admissionTypes` | `GENERAL`\|`MEISTER`\|`SOCIAL` 목록 | X | |
| `graduationStatuses` | `EXPECTED`\|`GRADUATED`\|`GED` 목록 | X | |
| `isArrived` | boolean | X | **이전 이름 `isSubmitted`** |
| `statuses` | `PENDING`\|`FIRST_PASS`\|`FIRST_FAIL`\|`FINAL_PASS`\|`FINAL_FAIL` 목록 | X | |
| `page` | int | X | 1부터. 기본 1 |
| `size` | int | X | 기본 10, 최대 100 |

작성 중(`DRAFT`)이거나 취소된(`CANCELED`) 원서는 지원자가 아니므로 목록에 없다.

### RESPONSE

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "applicantId": 1,
        "name": "홍길동",
        "region": "DAEJEON",
        "admissionType": "GENERAL",
        "graduationStatus": "EXPECTED",
        "examineeNumber": "100001",
        "isArrived": true,
        "status": "PENDING"
      }
    ],
    "page": 1,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1
  },
  "timestamp": "2026-09-18T13:38:16.659838Z"
}
```

## 2. 지원자 상세

`GET /api/v11/admin/applicants/{applicantId}`

### RESPONSE

```json
{
  "success": true,
  "data": {
    "applicantId": 1,
    "name": "홍길동",
    "birthDate": "2010-03-02",
    "phoneNumber": "010-1234-5678",
    "region": "DAEJEON",
    "admissionType": "GENERAL",
    "graduationStatus": "EXPECTED",
    "schoolName": "대덕중학교",
    "examineeNumber": "100001",
    "isArrived": true,
    "status": "PENDING",
    "score": { "totalScore": 172.5 },
    "submittedAt": "2026-09-10T12:30:00Z",
    "arrivedAt": "2026-09-12T01:00:00Z",
    "updatedAt": "2026-09-12T01:00:00Z",
    "photoFileId": "photo_c2f877a986e7414781328fbdc838b4af",
    "introduction": "저는 홍길동입니다.\n둘째 줄",
    "studyPlan": "1학년: 알고리즘\n2학년: 서버"
  },
  "timestamp": "2026-09-18T13:38:16.659838Z"
}
```

`score` 는 application 이 산출한 총점이 없으면 `null` 이다.

| 필드 | 설명 |
| --- | --- |
| `photoFileId` | 증명사진 ID. 올리지 않았으면 `null` |
| `introduction` | 자기소개서. 지원자가 쓴 줄바꿈(`\n`)이 그대로 있다. 쓰지 않았으면 `null` |
| `studyPlan` | 학업계획서. `introduction` 과 같다 |

증명사진 파일은 document 가 준다. `GET /api/document/v11/photos/{photoFileId}` 를 관리자 토큰으로 부르면
`data.downloadUrl`(서명 URL, `expiresIn` 초 동안 유효)이 온다.

지원자 한 명의 수험표·원서 원본 파일은 document 가 같은 `applicantId` 로 준다
(`GET /api/document/v11/admission-tickets/{applicantId}`, `/applications/{applicantId}`, #195).

## 3. 원본 도착 표시

`PATCH /api/v11/admin/applicants/{applicantId}/arrival`

### REQUEST

```json
{ "isArrived": true }
```

`isArrived` 는 필수다(이전 이름 `isSubmitted`). `true` 로 바꿀 때 `arrivedAt` 이 비어 있으면 그때 시각을 채우고, `false` 로 되돌리면 지운다. 이 요청이 그 지원자의 `screening` 행을 처음 만든다.

## 4. 전형 상태 정정

`PATCH /api/v11/admin/applicants/{applicantId}/status`

```json
{ "status": "FIRST_PASS", "force": false, "reason": null }
```

바뀐 것이 없다. `force` 가 `true` 면 `reason` 이 반드시 있어야 하고(없으면 400 `INVALID_REQUEST_BODY`), `false` 면 정상 전이만 받는다(아니면 409 `INVALID_STATUS_TRANSITION`).

## 5. 수험 번호 일괄 발급

`POST /api/v11/admin/examinee-numbers/issue`

원서 원본이 도착한 지원자에게만 **`applicantId` 오름차순**으로 100001 부터 빈 번호 없이 발급한다(이전에는 접수 번호 순). 이미 번호가 있으면 건너뛴다. 응답은 그대로다.

```json
{ "success": true, "data": { "issuedCount": 2, "skippedCount": 1, "totalTargets": 3 } }
```

## 6. 성적 정책 교체

`PATCH /api/v11/admin/score-policy`

### REQUEST

```json
{ "weights": { "subject": 0.8, "attendance": 0.1, "volunteer": 0.1 }, "roundingScale": 2 }
```

`recalculate` 를 **받지 않는다**. 총점은 application 이 전형별 상한(일반 173, 특별 119)에 맞춰 산출하므로 admin 이 다른 가중치로 다시 곱하지 않는다. 정책은 기록으로만 남는다.

`GET /api/v11/admin/score-policy` 응답은 그대로다.

## application gRPC 계약

admin 이 부르는 쪽이다. `contracts/proto/application.proto`.

```proto
rpc GetApplicant(GetApplicantRequest) returns (ApplicantResponse);
rpc ListApplicants(ListApplicantsRequest) returns (ListApplicantsResponse);
rpc GetApplicationForm(GetApplicationFormRequest) returns (ApplicationFormResponse);
```

- 지원자 상세는 `GetApplicant` 가 준 `user_id` 로 `GetApplicationForm` 을 한 번 더 불러 자기소개서·학업계획서·증명사진 ID 를 읽는다. `ApplicantResponse` 에 얹지 않는 것은 `ListApplicants` 가 제출 원서 전체의 본문을 한 메시지로 나르게 되기 때문이다. document 가 관리자 원서 출력에 쓰는 순서와 같다
- `ListApplicants` 는 인자가 없고, `SUBMITTED`·`REVIEWING`·`COMPLETED` 상태의 원서 전체를 `applicantId` 순으로 준다. 필터·페이징은 admin 이 자기 전형 정보와 합친 뒤에 건다(수험 번호 검색이 admin 쪽 값이라 그 전에는 걸 수 없다)
- `ApplicantResponse` 에 `birthdate`(ISO-8601 문자열), `phone_number`, `graduation_type`, `total_score`, `applicant_status`, `submitted_at_epoch_millis` 를 더했다. 기존 필드 번호는 그대로라 configuration·document 에 영향이 없다

## 배포

1. **application 을 먼저** 올리고 `/actuator/health/readiness` 를 확인한다
2. admin 을 올린다 (`V003` 이 `applicant` 를 지우고 `screening` 을 만든다)

새 admin 이 먼저 뜨면 `ListApplicants` 가 `UNIMPLEMENTED` 로 실패해 지원자 기능이 전부 503 이 된다. `deploy.yml` 은 `docker compose up -d` 로 한꺼번에 올려 순서를 보장하지 않으므로, #195 때처럼 호스트마다 `docker compose up -d application` 을 먼저 한다.

배포 전에 `SELECT COUNT(*) FROM applicant;` 를 확인한다. 넣는 코드가 없었으므로 0행이어야 하고, 0이 아니면 옮길 값을 따로 정한다.

admin 에 환경변수를 더한다: `APPLICATION_GRPC_HOST`, `APPLICATION_GRPC_PORT`, `APPLICATION_GRPC_DEADLINE_MS`(기본 3000), `APPLICATION_GRPC_LIST_DEADLINE_MS`(기본 10000).
