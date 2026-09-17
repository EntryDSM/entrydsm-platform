# 파일관리(document) API 규약 정리 컨텍스트

- 이슈: [#195](https://github.com/EntryDSM/entrydsm-platform/issues/195)
- 브랜치: `enhancement/195-document-api-conventions` (`feature/177-admission-ticket-generation` 93956b5c 에서 분기, PR #180 열림)
- 대상: configuration 서비스의 document API(`/api/document/v11/**`), 그와 겹치는 admin API, application gRPC `GetApplicant`
- 기준 문서: Notion 「API 공통 규약」, 「에러 코드 규칙」, 「RESPONSE」 (API 명세서 DB 09-14 복제본 `3db2cdd7...`)

Notion 명세 12개 행을 공통 규약과 대조한 리뷰를 코드로 확인하고, 고칠 것을 정리한 문서다. 구현 중에 판단이 바뀌면 이 문서를 먼저 고친다.

## 1. 리뷰 확인 결과

`맞음`은 코드로 확인된 것, `다름`은 코드 사실이 리뷰와 달라 결정에 반영한 것이다.

### 1.1 공통 규약 위반

| # | 리뷰 | 확인 |
| --- | --- | --- |
| 1 | 리소스명이 전부 단수 | 맞음. `/application`, `/admission-ticket`, `/applicant-list`, `/photo`, `/attachment`, `/guideline` |
| 2 | URL 에 동사 `/download` 5개, 조회·다운로드가 같은 파일을 두 번 노출 | 맞음. `GET /application`(최근 파일)과 `GET /application/download?format=`(형식 지정)이 같은 원서다 |
| 3 | 식별자가 쿼리스트링 | 맞음. `receiptCode`, `attachmentId`, `guidelineId`, `fileName` |
| 4 | 순차 ID 노출, 사진만 접두사 없는 숫자 | 맞음. `FileReferenceId` 가 `{종류}_{files.id}`(AUTO_INCREMENT)다. 첨부·요강은 학생 전체가 받을 수 있어 번호를 훑으면 공지에 아직 안 붙인 첨부까지 받는다. 사진 `fileId` 는 `files.id` 그대로이고 application 이 `photo_file_id BIGINT` 로 저장한다(사진은 본인만 받아 훑기 위험은 없다) |
| 5 | 에러 코드에 도메인이 없거나 접두사가 세 갈래 | 절반 다름. 도메인 코드가 `FILE_`·`STORAGE_`·`PRESIGN_`·`INVALID_FILE_`·`APPLICANT_` 로 흩어진 건 맞다. 하지만 `AUTH_UNAUTHORIZED`·`ACCESS_DENIED`·`INTERNAL_SERVER_ERROR` 는 admin·identity 도 같은 이름을 쓰는 공통 코드라, document 만 `DOCUMENT_` 로 바꾸면 서비스 사이가 오히려 어긋난다 |

### 1.2 도메인 경계

| API | 확인 |
| --- | --- |
| admin `GET /api/v11/admin/applicants/{applicantId}/admission-ticket` | admin 이 `admin_db.applicant` 로 수험표를 따로 만들어 admin 버킷 `admission-ticket/admission_ticket_{접수번호}.pdf` 에 올린다. 양식(`AdmissionTicketHtml`)이 두 벌이고, `admin_db.applicant` 를 채우는 코드가 없어 실제로는 늘 404 다 |
| admin `GET /api/v11/admin/applicants/{applicantId}/application-document` | `application/application_{접수번호}.pdf` 를 찾는데 document 는 `dsm_Entry/Backend/application/...` 에 올린다. **document 로 올린 원서를 admin 은 찾지 못한다.** 키 접두사를 맞춘 #145 는 닫힌 브랜치로 유실됐다 |
| document `POST /applicant-list`, `GET /applicant-list/download` | 클라이언트가 만든 엑셀을 올려 두는 API. #163 부터 admin `POST /exports`(`APPLICANT_LIST`)가 서버에서 엑셀을 만들어 쓸 곳이 없다 |
| admin `GET /api/v11/admin/admission-ticket-jobs` | Notion 에만 있고 코드에 없다. 일괄 수험표는 `POST /exports`(`ADMISSION_TICKET`, ZIP)가 한다 |
| notification `GET /api/notification/v11/notifications/guideline` | 다름. 파일이 아니라 요강 본문(제목·설명·일정)이다. 파일과 겹치지는 않지만, 요강 **파일** ID 를 알 방법이 없는 건 맞다 |

### 1.3 소유권

- 선점 규칙은 맞다. `FileDocumentService.ownerOf` 가 그 `receiptCode` 로 원서를 처음 올린 학생을 본인으로 삼고, `receiptCode` 는 `[A-Za-z0-9_-]+` 인 아무 값이다. 남의 번호를 먼저 올리면 원래 학생은 403 을 받는다
- "수험번호의 주인은 시스템이 알고 있다"는 **지금은 다르다.**
  - admin 수험번호 발급(`ExamineeNumberPolicy`)은 원서 원본(우편)이 도착한 지원자에게만 100001 부터 준다. 원서 파일을 올리는 시점보다 늦다
  - 발급 대상인 `admin_db.applicant` 를 채우는 코드가 없다
  - 원서를 가진 application 에는 접수번호도 수험번호도 없다. 지원자는 `applicants.id`(applicantId)와 `account_id`(userId)로만 식별된다
  - 학생이 자기 `receiptCode` 를 알아낼 API 가 없다. 유실된 #145 설계에서는 document `receiptCode` = admin **접수번호**이고 admin `applicantId` = application `applicantId` 였다. 명세의 "수험번호" 표기와도 어긋난다
- 발급 전에는 원서를 못 올리는 순서 문제는 맞다

### 1.4 내부 일관성

- 적재 응답 네 모양, 사진만 `url`·`expiresIn` 없음, `key` 노출: 맞음
- "첨부·요강 다운로드에만 `STORAGE_UNAVAILABLE` 이 없다": 다름. 그 둘은 DB 로 찾고 서명만 해서 S3 를 부르지 않는다. 원서 조회의 `exists` 도 S3 가 아니라 `files` 테이블로 판정한다. 명세는 코드와 맞다. 다만 호출마다 가능한 저장소 오류가 달라 헷갈리는 건 맞다
- `PRESIGN_FAILED` 와 `STORAGE_UNAVAILABLE` 의 기준: presign 은 로컬 서명이라 자격 증명을 못 읽을 때만 실패한다. 클라이언트가 할 일(잠시 뒤 재시도)은 같다

### 1.5 빠진 API, 문서 오류

- DELETE·목록·사진 조회 없음: 맞음
- 수험표 조회 없음: PR #180 이 `GET /admission-ticket`(생성)으로 바꿨다. 이 DB 복제본은 아직 `수험표 저장 POST` 다
- `end point` 에 굵게 표시가 섞인 3건(원서 저장·조회·다운), `예외` relation 3건(수험표 저장에 원서 것이 복사됨), 원서 저장 403 문구, 공통 규약 "지향"(→ 지양)·`v1`/`v11` 불일치: 모두 맞음. `예외` 가 가리키는 페이지는 연결 권한 밖이라 내용을 확인할 수 없다

## 2. 결정

### 2.1 파일은 document 가 갖는다

- 원서·수험표·증명사진·첨부·요강 파일의 오너는 document 다. #177 에서 수험표 생성을 document 로 정한 것과 같은 방향이다
- admin 의 개별 수험표·원서 원본 API 두 개를 지운다. 관리자 화면도 document 를 같은 applicantId 로 부른다
- document 의 지원자 목록 엑셀 API 두 개를 지운다. 내보내기 작업 산출물(엑셀, 수험표 ZIP)은 작업 리소스에 딸린 것이라 admin `exports` 에 둔다
- notification 요강 본문은 그대로 두고, document 에 요강 파일 목록을 둔다

### 2.2 원서·수험표는 applicantId 로 찾고 application 에 본인을 묻는다

- 경로 키는 application 의 `applicantId` 다. 원서 작성 API(`/api/application/v11/applicants/{id}`)와 같은 값이고, #145 설계의 admin `applicantId` 와도 같다. 학생은 `POST /applicants` 응답으로 이미 안다
- 본인 판정은 application gRPC `GetApplicant(applicant_id)` 가 돌려준 `user_id` 와 요청자(`X-User-Id`)를 비교한다. 선점 규칙, `receiptCode`, 원서 파일의 본인 추적(`ownerOf`)을 지운다
- 지원자가 없으면 404 `APPLICANT_NOT_FOUND`, application 호출 실패는 503 `APPLICATION_SERVICE_UNAVAILABLE`
- 수험표의 수험번호 칸: 발급값을 가진 곳에서 받을 길이 없어 `미발급` 으로 찍는다(admin 수험표와 같은 표기). 클라이언트가 보낸 `receiptCode` 를 찍던 것은 검증되지 않은 값이었다

### 2.3 경로

기준 경로 `/api/document/v11`. 조회 응답에 서명 URL 을 넣어 다운로드 경로를 없앤다.

| 기능 | 이전 | 이후 | 권한 |
| --- | --- | --- | --- |
| 원서 적재 | `POST /application?receiptCode=` | `PUT /applications/{applicantId}` | 본인, 관리자 |
| 원서 조회 | `GET /application?receiptCode=`, `GET /application/download` | `GET /applications/{applicantId}` | 본인, 관리자 |
| 수험표 | `GET /admission-ticket?receiptCode=`, `GET /admission-ticket/download` | `GET /admission-tickets/{applicantId}` (만들고 URL 반환) | 본인, 관리자 |
| 지원자 목록 엑셀 | `POST /applicant-list`, `GET /applicant-list/download` | 삭제 (admin `POST /exports`) | |
| 증명사진 적재 | `POST /photo` | `POST /photos` | 학생 |
| 증명사진 조회 | 없음 | `GET /photos/{photoId}` | 본인, 관리자 |
| 첨부 적재 | `POST /attachment` | `POST /attachments` | 관리자 |
| 첨부 조회 | `GET /attachment/download?attachmentId=` | `GET /attachments/{attachmentId}` | 학생, 관리자 |
| 첨부 삭제 | 없음 | `DELETE /attachments/{attachmentId}` → 204 | 관리자 |
| 요강 적재 | `POST /guideline` | `POST /guidelines` | 관리자 |
| 요강 목록 | 없음 | `GET /guidelines?page=1&size=10` (최근 순) | 학생, 관리자 |
| 요강 조회 | `GET /guideline/download?guidelineId=` | `GET /guidelines/{guidelineId}` | 학생, 관리자 |
| 요강 삭제 | 없음 | `DELETE /guidelines/{guidelineId}` → 204 | 관리자 |

- 원서는 pdf·hwp 중 가장 최근에 올린 것을 돌려준다. `format` 파라미터는 없앤다
- 원서 적재는 지원자 한 명의 파일을 통째로 바꾸므로 `PUT` 이다 (멀티파트). 멀티파트가 아니거나 `file` 이 없으면 400
- 요강 목록은 `page` 1 이상, `size` 1~100 (기본 1, 10). 벗어나면 400
- 삭제는 파일 행을 먼저 지우고 저장소 객체를 지운다. 객체 삭제가 실패해도 204 이고 로그만 남는다. 학생이 지울 수 있는 종류가 생겨도 자기가 올린 것만 지우게 권한표에 `canDelete` 를 둔다
- 적재는 서명 URL 을 먼저 발급하고 올린다. 서명이 실패하면 아무것도 저장되지 않아, 실패 응답을 받은 클라이언트가 다시 올려도 파일이 쌓이지 않는다
- 원서·수험표는 지원자마다 저장 키가 하나라 같은 지원자의 동시 요청이 객체를 같이 쓴다. 행 저장이 실패해도 객체를 지우지 않고 한 번 다시 저장한다(먼저 만들어진 행을 갱신). 요청마다 새 키를 쓰는 사진·첨부·요강만 실패 시 객체를 지운다

### 2.4 ID

- 사진·첨부·요강 ID 는 `{종류}_{32자 임의값}` (`photo_3f2c...`, `attachment_...`, `guideline_...`). `files.public_id` 컬럼에 이 값을 통째로 저장한다(configuration V003). 기존 행은 마이그레이션이 `RANDOM_BYTES` 로 채운다(`UUID()` 는 시간 기반이라 이웃 값을 짐작할 수 있다)
- ID 는 그대로 찾기만 하므로 형식이 틀린 ID 는 400 이 아니라 404 `FILE_NOT_FOUND` 다. 다른 종류의 ID(`GET /guidelines/attachment_...`)도 404
- application 의 `photoFileId` 는 문자열이 된다 (REST 요청, `applicants.photo_file_id` VARCHAR(64), gRPC `photo_file_id`). develop 에 `V004__create_institution_codes`(#190)가 있어 application 마이그레이션은 V005 다
- V005 는 타입만 바꿔 예전 숫자 사진 ID 가 `"123"` 으로 남는다. 수험표를 만들 때 숫자면 `files.id` 로도 찾는다(사진 종류·본인 확인은 같다). 운영 값이 모두 `photo_` 로 시작하면 이 분기를 지운다
- 원서·수험표는 applicantId 로 찾으므로 파일 ID 가 없다

### 2.5 응답

`key` 를 빼고 한 모양으로 맞춘다.

```json
{
  "id": "attachment_3f2c9a1e0b7d4c55a1e2f3b4c5d6e7f8",
  "fileName": "notice.pdf",
  "size": 20480,
  "downloadUrl": "https://s3.../...?X-Amz-...",
  "expiresIn": 600
}
```

- 적재·조회 모두 이 모양이다. 원서·수험표는 `id` 가 없다
- `fileName`: 사진·첨부·요강은 올린 파일명, 원서·수험표는 저장 파일명(`application_{applicantId}.pdf`, `admission_ticket_{applicantId}.pdf`)
- 원서 조회만 아직 안 올린 경우를 404 대신 `{ "exists": false }` 로 준다. 올렸으면 `exists: true` 와 위 필드
- 요강 목록은 공통 목록 응답(`items`, `page`, `size`, `totalElements`, `totalPages`)에 위 모양을 담는다

### 2.6 에러 코드

인증·역할·요청 형식처럼 서비스와 무관한 코드는 admin·identity 와 같은 공통 코드로 두고, 에러 코드 규칙 문서에 공통 코드 목록을 적는다. 나머지는 `{도메인}_{이유}` 로 맞춘다.

| 이전 | 이후 | HTTP |
| --- | --- | --- |
| `INVALID_REQUEST_PARAM`, `AUTH_UNAUTHORIZED`, `METHOD_NOT_ALLOWED`, `INTERNAL_SERVER_ERROR` | 그대로 (공통) | 400, 401, 405, 500 |
| `ACCESS_DENIED` (역할 헤더가 학생·관리자가 아님) | 그대로 (공통) | 403 |
| `ACCESS_DENIED` (그 파일에 권한 없음) | `FILE_ACCESS_DENIED` | 403 |
| `INVALID_FILE_FORMAT` | `FILE_INVALID_FORMAT` | 400 |
| `FILE_NOT_FOUND`, `FILE_TOO_LARGE` | 그대로 | 404, 413 |
| `STORAGE_UPLOAD_FAILED`, `PRESIGN_FAILED`, `STORAGE_UNAVAILABLE` | `FILE_STORAGE_UNAVAILABLE` | 502 |
| `APPLICANT_NOT_FOUND` | 그대로 | 404 |
| `APPLICANT_LOOKUP_FAILED` (502) | `APPLICATION_SERVICE_UNAVAILABLE` (identity 와 같은 이름) | 503 |

## 3. 영향과 배포 전 확인

- 프론트: document 경로·응답·에러 코드가 전부 바뀐다. 사진 ID 를 원서 인적사항에 문자열로 보낸다
- application 과 configuration 을 같이 배포한다 (gRPC 필드 번호를 새로 써서 섞여 떠도 잘못된 지원자를 돌려주지는 않고 404 가 난다)
- 옛 경로(`/application`, `/photo` …)는 없는 경로라 지금은 catch-all 이 500 을 준다. 게이트웨이 서킷이 5xx 를 세므로 없는 경로를 404 로 바꾸는 PR #170 을 먼저 또는 같이 배포한다. #170 과 이 브랜치는 configuration 의 `DocumentExceptionHandler`·`ErrorCode`·`DocumentApiContractTest` 가 겹친다
- 운영 데이터 확인·이전과 롤백은 아래 3.1~3.3 을 따른다. 롤백은 운영 데이터가 없어도 스키마 때문에 SQL 이 필요하다
- 관리자 화면이 document 를 부르려면 admin 지원자 응답의 `applicantId` 가 application 의 applicantId 여야 한다. admin 지원자 데이터 공백(#145 유실)과 함께 풀어야 한다

### 3.1 배포 전 확인

이전 버전 스키마에서 돌린다. 모두 0 이면 3.2 는 건너뛴다.

```sql
-- configuration_db: receiptCode 로 올린 원서·수험표. 새 코드는 applicantId 키로 찾아 이 파일들을 못 찾는다
SELECT COUNT(*) FROM files
WHERE object_key LIKE 'dsm\_Entry/Backend/application/%'
   OR object_key LIKE 'dsm\_Entry/Backend/admission-ticket/%';

-- notification_db: 첨부가 붙은 공지. attachment_{순번} 형식은 새 코드에서 404 다
SELECT COUNT(*) FROM notices WHERE attachment_ids IS NOT NULL AND attachment_ids <> '';

-- application_db: 숫자 사진 ID. 코드가 계속 찾으므로 옮기지 않아도 되고, 숫자 분기를 지울 시점만 판단한다
SELECT COUNT(*) FROM applicants WHERE photo_file_id IS NOT NULL;
```

### 3.2 데이터가 있을 때 옮기기

configuration V003 과 application V005 가 적용된 뒤, 새 버전으로 트래픽을 받기 전에 한다.

- 수험표: 요청할 때마다 다시 만들므로 옮기지 않는다. 옛 `admission_ticket_{receiptCode}` 행과 객체는 지워도 된다
- 원서: 행마다 `owner_user_id`(올린 학생 계정)로 `application_db.applicants.account_id` 를 찾아 applicantId 를 얻고, S3 객체를 `dsm_Entry/Backend/application/application_{applicantId}.{확장자}` 로 복사한 뒤 `files.object_key` 를 새 키로 바꾼다. `owner_user_id` 가 없는 행(관리자가 먼저 올림)은 지원자를 알 수 없어 따로 확인한다
- 공지 첨부: `configuration_db.files` 에서 첨부 행의 `id`·`public_id` 대응을 뽑아, `notices.attachment_ids` 안의 `attachment_{id}` 를 그 `public_id` 로 바꾼다. 쉼표로 이은 문자열이라 SQL 보다 일회성 스크립트가 안전하다
- 사진 ID: 옮기지 않는다 (2.4)

### 3.3 롤백

이전 버전은 두 마이그레이션이 적용된 스키마에서 동작하지 않는다.

- configuration 이전 버전은 `public_id`(NOT NULL, 기본값 없음)를 넣지 않아 파일 적재 INSERT 가 실패한다
- application 이전 버전은 엔티티가 `Long` 이라 `photo_file_id` VARCHAR 에서 `ddl-auto: validate` 로 기동하지 못한다

새 버전을 내리고 SQL 을 돌린 뒤 이전 버전을 올린다. 프론트도 이전 경로로 함께 되돌린다. `flyway_schema_history` 행을 지우지 않으면 다시 배포할 때 마이그레이션이 적용되지 않는다.

```sql
-- configuration_db
ALTER TABLE files DROP INDEX uk_files_public_id, DROP COLUMN public_id;
DELETE FROM flyway_schema_history WHERE script = 'V003__add_file_public_id.sql';

-- application_db: 새 형식 사진 ID(photo_...)는 되돌릴 수 없어 비운다. 학생이 사진을 다시 골라야 한다
UPDATE applicants SET photo_file_id = NULL WHERE photo_file_id NOT REGEXP '^[0-9]+$';
ALTER TABLE applicants MODIFY COLUMN photo_file_id BIGINT NULL;
DELETE FROM flyway_schema_history WHERE script = 'V005__change_photo_file_id_to_string.sql';
```

3.2 로 옮긴 데이터(원서 키, 공지 첨부 ID)는 롤백 SQL 로 되돌아가지 않는다. 옮기기 전 대응표를 남겨 둔다.

## 4. 하지 않는 것

- admin 일괄 수험표 ZIP 의 양식 중복: admin 지원자 데이터가 생긴 뒤 document 수험표를 쓰게 한다
- 수험번호를 application(또는 이벤트)으로 넘기는 흐름: 발급 대상 데이터가 먼저다
- 사진 삭제, 첨부 목록, 비로그인 첨부 다운로드
- 공통 규약 8항(동기 통신은 REST)과 실제 gRPC 의 차이

## 5. Notion 명세 수정

- document: 위 2.3 표대로 행을 고치고 새 행(사진 조회, 첨부 삭제, 요강 목록·삭제)을 만든다. 없어지는 행(원서 다운, 수험표 다운, 지원자 목록 excel 저장·다운)은 제목에 `(삭제)` 를 붙이고 대체 API 를 적는다
- `end point` 의 굵게 표시를 지우고, `예외` relation 은 비운다 (에러 코드는 각 행 본문 표에 있다)
- admin: 수험표 다운로드(개별), 원서 원본 다운로드, 수험표 다운(`admission-ticket-jobs`) 행에 `(삭제)` 와 대체 API
- notification 전형 요강 조회: 파일은 document `GET /guidelines` 라고 적는다
- 공통 규약: "지향" → "지양", 예시 경로 `v1` → `v11`
- 에러 코드 규칙: 공통 코드 목록

## 6. 검증

- `bazel test` application·configuration·admin 전부 통과. 워크스페이스 빌드는 gateway 테스트 타깃(`spring_security_test` 의존성 없음, 이 작업과 무관)만 빼고 통과
- 로컬 E2E (origin/develop 을 임시로 합친 트리, MySQL 8.4·Redis·MinIO, application·configuration 을 바이너리로 실행)
  - 빈 DB 에서 application V001~V005, configuration V001~V003 적용 후 스키마 검증 통과. V003 백필은 기존 행이 있는 DB 에 따로 돌려 행마다 다른 임의값이 들어가는 것을 확인
  - 사진: 학생 적재 → 본인·관리자 200, 다른 학생 403 `FILE_ACCESS_DENIED`, 서명 URL 로 받은 바이트 일치. 그 ID 를 application `PATCH /applicants/1/personal` 로 저장
  - 원서: 본인 PUT 200(서명 URL 바이트 일치), 다른 학생 403, 관리자 덮어쓰기 200(본인 계정·공개 ID 유지), 없는 지원자 학생 403·관리자 404, JSON 본문·파일 없음 400, jpg 400 `FILE_INVALID_FORMAT`. 조회: 올리기 전 `exists: false`, 본인·관리자 200, 다른 학생 403, 숫자 아닌 ID 400
  - 수험표: 본인 200 → PDF 에 성명·중학교·지역·전형·본인 사진과 수험번호 `미발급`. 다른 학생 403, 없는 지원자 관리자 404, POST 405
  - 첨부: 관리자 적재 200, 학생 적재 403, 학생 조회 200, 요강 경로로 조회 404, 학생 삭제 403, 관리자 삭제 204 → 조회 404, MinIO 객체도 지워짐
  - 요강 목록: 2개 적재 후 `page=1&size=1` 은 최근 것, `page=2` 는 이전 것, `totalElements` 2·`totalPages` 2, `size=101` 400
  - application 을 끈 뒤 원서·수험표 503 `APPLICATION_SERVICE_UNAVAILABLE`, 요강 목록은 200
  - 옛 경로는 500 (위 3절, #170)
