# 지원자 목록 엑셀 다운로드 API 명세

- 이슈: [#163](https://github.com/EntryDSM/entrydsm-platform/issues/163)
- API 소유: admin. 엑셀은 비동기 내보내기 작업이 만들어 저장소(S3)에 올리고, 서명된 링크로 내려받는다

| 순서 | 기능 | Method | End point | 인증 | 성공 |
| --- | --- | --- | --- | --- | --- |
| 1 | 내보내기 접수 | `POST` | `/api/v11/admin/exports` | O (ADMIN) | `202 Accepted` |
| 2 | 작업 상태 조회 | `GET` | `/api/v11/admin/exports/{exportJobId}` | O (ADMIN) | `200 OK` |
| 3 | 파일 내려받기 | `GET` | 2번 응답의 `downloadUrl` | X (서명된 링크) | `200 OK` |

```text
POST /api/v11/admin/exports {"type": "APPLICANT_LIST", "filter": {...}}
  → 202 {exportJobId, status: PENDING}
GET  /api/v11/admin/exports/{exportJobId}
  → PENDING / PROCESSING 이면 다시 조회
  → COMPLETED 이면 downloadUrl 로 이동 → applicants_<exportJobId>.xlsx
  → FAILED 이면 다시 접수
```

## 이전과 달라진 점

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| `APPLICANT_LIST` 산출물 | CSV 9열 (`applicants_<exportJobId>.csv`) | xlsx 15열 (`applicants_<exportJobId>.xlsx`) |
| 필터 적용 | 저장 과정에서 사라져 **항상 전체 지원자**가 나감 (수험표 묶음 포함) | 보낸 조건대로 거름 |
| 필터 조건 | `admissionTypes`, `statuses` | 목록 조회와 같은 6개 조건 |
| 목록 조건에 `null` | 400 | 조건 없음으로 받음 |

## 공통

### 인증

게이트웨이가 토큰을 확인하고 `X-User-Id`, `X-User-Role` 헤더를 넣어 admin 으로 넘긴다. admin 은 두 헤더가 없으면 401, 역할이 `ADMIN` 이 아니면 403 을 돌려준다. 3번 서명된 링크는 게이트웨이를 거치지 않고 저장소로 바로 간다.

### 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "EXPORT_JOB_NOT_FOUND",
    "message": "Export 작업을 찾을 수 없습니다.",
    "status": 404
  },
  "timestamp": "2026-09-14T13:38:16.659838Z"
}
```

## 1. 내보내기 접수

`POST /api/v11/admin/exports`

작업을 만들고 바로 `202` 를 돌려준다. 엑셀은 응답 뒤에 따로 만든다.

### REQUEST

```json
{
  "type": "APPLICANT_LIST",
  "filter": {
    "keyword": "홍길",
    "regions": ["DAEJEON"],
    "admissionTypes": ["GENERAL", "MEISTER"],
    "graduationStatuses": ["EXPECTED"],
    "isSubmitted": true,
    "statuses": ["FIRST_PASS"]
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `type` | string | O | `APPLICANT_LIST`: 지원자 목록 엑셀. (`ADMISSION_TICKET`: 1차 합격자 수험표를 수험번호 순으로 이어 붙인 PDF 하나) |
| `filter` | object | X | 없거나 `null` 이면 전체 지원자 |
| `filter.keyword` | string | X | 이름 또는 수험번호 부분 일치. 대소문자·앞뒤 공백 무시, 공백만 있으면 거르지 않음 |
| `filter.regions` | string[] | X | 모집 지역 |
| `filter.admissionTypes` | string[] | X | 전형 |
| `filter.graduationStatuses` | string[] | X | 학력 |
| `filter.isSubmitted` | boolean | X | 원서 원본(우편) 도착 여부 |
| `filter.statuses` | string[] | X | 전형 진행 상태 |

- 조건끼리는 AND, 한 목록 안의 값끼리는 OR 로 거른다
- 필드 없음, `null`, 빈 목록 `[]` 은 모두 그 조건으로 거르지 않는다
- 지원자 목록 조회(`GET /api/v11/admin/applicants`)의 쿼리 파라미터와 이름·값이 같다. 화면의 검색 조건을 그대로 넣으면 같은 지원자가 나온다 (`page`, `size` 없이 전부)
- `ADMISSION_TICKET` 은 보낸 `statuses` 를 버리고 1차 합격자(`FIRST_PASS`)만 뽑는다. 나머지 조건은 똑같이 적용된다. 수험표 한 장씩은 document 가 증명사진을 넣어 그린다(#254, `documents/features/admission-ticket-print`)

조건 값과 엑셀 표기

| 필드 | 값 → 엑셀 표기 |
| --- | --- |
| `regions` | `DAEJEON` 대전, `NATIONWIDE` 전국 |
| `admissionTypes` | `GENERAL` 일반전형, `MEISTER` 마이스터전형, `SOCIAL` 사회통합전형 |
| `graduationStatuses` | `EXPECTED` 졸업예정, `GRADUATED` 졸업, `GED` 검정고시 |
| `statuses` | `PENDING` 심사 대기, `FIRST_PASS` 1차 합격, `FIRST_FAIL` 1차 불합격, `FINAL_PASS` 최종 합격, `FINAL_FAIL` 최종 불합격 |

### RESPONSE `202 Accepted`

```json
{
  "success": true,
  "data": {
    "exportJobId": "exp_3b5d00f6fa234579b85bb4640fc3da6d",
    "status": "PENDING"
  },
  "error": null
}
```

### 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST_BODY` | `type` 없음, 알 수 없는 값(`"regions": ["SEOUL"]`), 타입 오류(`"isSubmitted": "yes"`), JSON 형식 오류 |
| 401 | `AUTH_UNAUTHORIZED` | 인증 헤더 없음 |
| 403 | `ACCESS_DENIED` | 관리자 권한 아님 |
| 409 | `ADMISSION_TICKET_NO_TARGET` | `ADMISSION_TICKET` 인데 조건에 맞는 1차 합격자가 없음 (1차 산출 전 등). 메시지 "수험표를 발급할 1차 합격자가 없습니다." |
| 500 | `INTERNAL_SERVER_ERROR` | 작업 저장 실패 |
| 503 | `APPLICATION_SERVICE_UNAVAILABLE` | `ADMISSION_TICKET` 대상 확인 중 application 장애 |

## 2. 작업 상태 조회

`GET /api/v11/admin/exports/{exportJobId}`

| Path Variable | 타입 | 설명 |
| --- | --- | --- |
| `exportJobId` | string | 1번 응답의 `exportJobId` |

### RESPONSE `200` — 완료

```json
{
  "success": true,
  "data": {
    "exportJobId": "exp_3b5d00f6fa234579b85bb4640fc3da6d",
    "type": "APPLICANT_LIST",
    "status": "COMPLETED",
    "downloadUrl": "https://entrydsm-admin.s3.ap-northeast-2.amazonaws.com/applicant-list/applicants_exp_3b5d00f6fa234579b85bb4640fc3da6d.xlsx?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&X-Amz-Signature=...",
    "expiresAt": "2026-09-14T13:52:21.033062Z",
    "createdAt": "2026-09-14T13:37:20.270603Z",
    "completedAt": "2026-09-14T13:37:20.660777Z"
  },
  "error": null
}
```

### RESPONSE `200` — 실패

```json
{
  "success": true,
  "data": {
    "exportJobId": "exp_7312c85abe5c4aeba2ff2f0353758074",
    "type": "APPLICANT_LIST",
    "status": "FAILED",
    "downloadUrl": null,
    "expiresAt": null,
    "createdAt": "2026-09-14T13:38:31.439512Z",
    "completedAt": "2026-09-14T13:38:31.608742Z"
  },
  "error": null
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `exportJobId` | string | 작업 식별자 |
| `type` | string | 접수한 `type` |
| `status` | string | 아래 표 |
| `downloadUrl` | string \| null | `COMPLETED` 일 때만 있다. 조회할 때마다 새로 발급한다 |
| `expiresAt` | string \| null | `downloadUrl` 만료 시각 (UTC). 발급 후 15분(`DOWNLOAD_URL_EXPIRES_SECONDS`) |
| `createdAt` | string | 접수 시각 (UTC) |
| `completedAt` | string \| null | 완료 또는 실패 시각 (UTC) |

| `status` | 뜻 | 프론트 처리 |
| --- | --- | --- |
| `PENDING` | 접수됨 | 1초쯤 뒤 다시 조회 |
| `PROCESSING` | 지원자 조회·엑셀 생성·업로드 중 | 1초쯤 뒤 다시 조회 |
| `COMPLETED` | 완료 | `downloadUrl` 로 이동 |
| `FAILED` | 조회·생성·업로드 중 오류 | 다시 접수 |

- 링크가 만료되면 이 API 를 다시 부르면 새 링크가 나온다. 파일은 저장소에 그대로 있어 다시 만들 필요가 없다
- 작업은 서버 메모리에서 처리한다. 처리하던 서버가 내려가면 `PENDING`·`PROCESSING` 에서 멈추므로, 오래(예: 1분) 끝나지 않으면 다시 접수한다

### 오류

| HTTP | code | 상황 |
| --- | --- | --- |
| 401 | `AUTH_UNAUTHORIZED` | 인증 헤더 없음 |
| 403 | `ACCESS_DENIED` | 관리자 권한 아님 |
| 404 | `EXPORT_JOB_NOT_FOUND` | 없는 작업 |
| 500 | `STORAGE_UNAVAILABLE` | 다운로드 링크 발급 실패 |

## 3. 파일 내려받기

`GET {downloadUrl}`

- 응답: `200`, `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- **인증 헤더를 붙이지 않는다.** 서명이 쿼리에 들어 있어서 `Authorization` 을 함께 보내면 저장소가 400 으로 거절한다. 토큰을 자동으로 붙이는 공통 HTTP 클라이언트 대신 페이지 이동(`window.location.href = downloadUrl`)으로 연다
- 파일 이름은 링크 경로의 마지막 부분인 `applicants_<exportJobId>.xlsx` 로 저장된다
- 수험표(`ADMISSION_TICKET`)는 `Content-Type: application/pdf`, 파일 이름 `admission_tickets_<exportJobId>.pdf` 다. 1차 합격자 한 명이 한 쪽이고 수험번호 순이다. 새 창으로 열면 브라우저 PDF 뷰어로 보여 바로 인쇄할 수 있다

### 엑셀 구성

- 시트 하나(`지원자 목록`). 1행은 머리글이고 틀 고정되어 있다
- 2행부터 지원자 한 명씩, 접수번호 오름차순
- 글자 칸은 수식으로 실행되지 않는다 (`=1+1` 인 값도 글자 그대로 보인다)

| 열 | 머리글 | 칸 | 예시 | 비고 |
| --- | --- | --- | --- | --- |
| A | 접수번호 | 글자 | `0001` | 네 자리로 채운다. 9999 번을 넘으면 자릿수가 늘어난다 |
| B | 수험번호 | 글자 | `11001` | 발급 전이면 빈 칸 |
| C | 성명 | 글자 | `홍길동` | |
| D | 생년월일 | 글자 | `2010-03-15` | |
| E | 연락처 | 글자 | `010-1111-2222` | |
| F | 지역 | 글자 | `대전` | 1번 표기 표 |
| G | 전형 | 글자 | `일반전형` | |
| H | 학력 | 글자 | `졸업예정` | |
| I | 출신학교 | 글자 | `대전가양중학교` | |
| J | 원서 도착 | 글자 | `도착` / `미도착` | |
| K | 상태 | 글자 | `1차 합격` | |
| L | 교과 점수 | 숫자 | `90.5` | 가중치 적용 전 원점수. 성적 산출 전이면 빈 칸 |
| M | 출결 점수 | 숫자 | `15` | 〃 |
| N | 봉사 점수 | 숫자 | `10` | 〃 |
| O | 총점 | 숫자 | `170.25` | 성적 정책 가중치를 적용해 반올림한 값. 산출 전이면 빈 칸 |

## 참고

- 파일에 연락처·생년월일이 들어간다. 만료되는 것은 링크뿐이고 파일은 저장소에 남는다
- 엑셀은 admin 의 지원자 테이블(`admin_db.applicant`)을 읽는다. develop 기준 이 테이블을 채우는 코드가 없어, 목록 조회와 마찬가지로 머리글만 나올 수 있다 (#163 Dependencies / Risks)
- configuration 의 `POST /api/document/v11/applicant-list`, `GET /api/document/v11/applicant-list/download` 는 이미 있는 xlsx 파일을 올리고 링크를 주는 파일 저장 API 로, 이 엑셀과 연결되어 있지 않다. Notion 의 "지원자 목록 excel 다운" 행이 그쪽을 가리키지만 본문에는 로그인 예시가 들어 있어 명세가 없다
- Notion API 명세 DB 의 "내보내기 (Export)", "Export 작업 상태 조회 / 다운로드" 행은 병합 후 이 문서대로 갱신한다
