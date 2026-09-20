# 원서 작성 API 명세 (원서 ID 제거)

- 이슈: [#210](https://github.com/EntryDSM/entrydsm-platform/issues/210)
- API 소유: application. 계정 하나에 원서는 하나(`applicants.account_id` UNIQUE)라 요청자 계정으로 원서를 찾는다

| 순서 | 기능 | Method | End point | 인증 | 성공 |
| --- | --- | --- | --- | --- | --- |
| 1 | 원서 생성 | `POST` | `/api/application/v11/applicants` | O (STUDENT) | `201 Created` |
| 2 | 전형 구분 | `PATCH` | `/api/application/v11/applicants/type` | O (STUDENT) | `200 OK` |
| 3 | 인적 사항 | `PATCH` | `/api/application/v11/applicants/personal` | O (STUDENT) | `200 OK` |
| 4 | 보호자·주소 | `PATCH` | `/api/application/v11/applicants/family` | O (STUDENT) | `200 OK` |
| 5 | 출신 중학교 | `PATCH` | `/api/application/v11/applicants/middle-school` | O (STUDENT) | `200 OK` |
| 6 | 자기소개서 | `PATCH` | `/api/application/v11/applicants/self-introduction` | O (STUDENT) | `200 OK` |
| 7 | 학업계획서 | `PATCH` | `/api/application/v11/applicants/study-plan` | O (STUDENT) | `200 OK` |
| 8 | 원서 제출 | `PATCH` | `/api/application/v11/applicants` | O (STUDENT) | `200 OK` |

1번과 8번은 이번 변경 전에도 원서 ID 를 받지 않았다. 2~7번이 이번에 바뀐 엔드포인트다.

## 이전과 달라진 점

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 2~7번 경로 | `/applicants/{applicantId}/type` 처럼 원서 ID 를 받음 | `/applicants/type` — 원서 ID 를 받지 않음 |
| 원서 찾는 방법 | 경로의 원서 ID 로 찾고 `accountId` 가 요청자와 다르면 거절 | 요청자 계정(`account_id`)으로 바로 찾음 |
| 남의 원서 지정 | `403 APPLICANT_ACCESS_DENIED` | **지정할 방법이 없음.** 이 오류 코드는 사라졌다 |
| 원서가 없을 때 | `404 APPLICANT_NOT_FOUND` | 같음 (먼저 1번으로 만들어야 한다) |
| 요청 본문 | — | 바뀌지 않았다 |

프런트는 2~7번을 부를 때 원서 ID 를 붙이지 않는다. 1번 응답의 `applicantId` 는 계속 필요하다 — document 서비스의 원서·수험표 API(`/api/document/v11/applications/{applicantId}`, `/api/document/v11/admission-tickets/{applicantId}`)가 그 값을 경로로 받는다. 거기서는 관리자가 남의 원서를 받으므로 원서 ID 가 그대로 남는다.

## 공통

### 인증

게이트웨이가 토큰을 확인하고 `X-User-Id`, `X-User-Role`, `user-id`, `X-Sensitive-Agree` 헤더를 넣어 application 으로 넘긴다. application 은 `X-User-Role` 이 `STUDENT` 가 아니면 403 `ACCESS_DENIED` 다. 원서를 찾을 때 쓰는 계정 번호는 `user-id` 헤더에서 읽는다(게이트웨이가 `X-User-Id` 와 같은 값을 넣어 준다). 2번만 `X-Sensitive-Agree` 를 함께 본다.

### 응답 봉투

```json
{ "success": true, "data": null }
```

### 오류 응답

```json
{
  "success": false,
  "error": {
    "code": "APPLICANT_NOT_FOUND",
    "message": "Applicant not found: 10",
    "status": 404
  },
  "timestamp": "2026-09-18T07:43:16.659838Z"
}
```

| 코드 | 상태 | 언제 |
| --- | --- | --- |
| `AUTHENTICATION_REQUIRED` | 401 | `X-User-Id` 헤더가 없거나 비었다 |
| `ACCESS_DENIED` | 403 | 역할이 `STUDENT` 가 아니다 |
| `SENSITIVE_CONSENT_REQUIRED` | 403 | 사회통합전형인데 민감정보 동의 헤더가 없다 |
| `APPLICANT_NOT_FOUND` | 404 | 요청자 계정에 원서가 없다 |
| `APPLICANT_ALREADY_EXISTS` | 409 | 1번을 두 번 불렀다 |
| `INVALID_REQUEST` | 400 | 본문 검증 실패, `user-id` 헤더 없음, 검정고시 지원자의 출신 중학교 수정 같은 규칙 위반 |

## 2. 전형 구분

`PATCH /api/application/v11/applicants/type`

### REQUEST

```json
{
  "admissionType": "REGULAR",
  "region": "DAEJEON",
  "graduationType": "EXPECTED",
  "graduationDate": "2027-02"
}
```

- `graduationType` 이 `GED` 면 `graduationDate` 는 `null` 이어야 하고, 아니면 반드시 있어야 한다
- `GED` 로 바꾸면 출신 중학교 정보와 교과 성적이 지워진다
- `admissionType` 이 `SOCIAL` 이면 `X-Sensitive-Agree: true` 가 필요하다

## 3. 인적 사항

`PATCH /api/application/v11/applicants/personal`

### REQUEST

```json
{
  "photoFileId": "photo_01H...",
  "name": "홍길동",
  "phoneNumber": "010-0000-0000",
  "gender": "MALE",
  "birthdate": "2010-03-02",
  "specialAdmissionType": "NONE"
}
```

- `birthdate` 는 `YYYY-MM-DD` 또는 `YYYY-MM`(1일로 읽는다)
- `specialAdmissionType` 은 없으면 `NONE`

## 4. 보호자·주소

`PATCH /api/application/v11/applicants/family`

### REQUEST

```json
{
  "guardianName": "홍부모",
  "guardianPhoneNumber": "010-0000-0000",
  "guardianGender": "FEMALE",
  "guardianRelation": "모",
  "address": {
    "zipCode": "34111",
    "addressBase": "대전광역시 유성구 ...",
    "addressDetail": "101동 101호"
  }
}
```

## 5. 출신 중학교

`PATCH /api/application/v11/applicants/middle-school`

### REQUEST

```json
{
  "schoolName": "대덕중학교",
  "studentNumber": "30101",
  "schoolPhone": "042-000-0000",
  "teacherName": "김담임"
}
```

- 검정고시(`GED`) 지원자는 400 이다

## 6. 자기소개서 / 7. 학업계획서

`PATCH /api/application/v11/applicants/self-introduction`
`PATCH /api/application/v11/applicants/study-plan`

### REQUEST

```json
{ "introduction": "..." }
```

```json
{ "studyPlan": "..." }
```

- 각각 1600자까지
