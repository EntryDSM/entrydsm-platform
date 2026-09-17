# 코드가 바뀐 API 목록

- 기준: `origin/develop` 대비 PR #180 (`feature/177-admission-ticket-generation` 70b9a945). #177(수험표 서버 생성)과 #195(document API 규약 정리)가 함께 들어 있다
- Notion 명세에 비고만 추가하고 코드는 바뀌지 않은 API 는 맨 아래에 따로 적었다
- identity·notification·gateway·observability API 는 바뀌지 않았다
- 결정과 근거: [context.md](context.md)

## document (`/api/document/v11`)

모든 API 가 바뀌었다. 응답은 `{ id, fileName, size, downloadUrl, expiresIn }` 으로 통일되고 `key` 가 빠졌으며, 에러 코드는 공통 코드와 `FILE_*` 코드로 바뀌었다.

| 이전 (develop) | 이후 (PR #180) | 비고 |
| --- | --- | --- |
| `POST /application?receiptCode=` | `PUT /applications/{applicantId}` | |
| `GET /application?receiptCode=`, `GET /application/download` | `GET /applications/{applicantId}` | |
| `POST /admission-ticket` (파일 업로드), `GET /admission-ticket/download` | `GET /admission-tickets/{applicantId}` | 서버가 생성 |
| `POST /photo` | `POST /photos` | |
| 없음 | `GET /photos/{photoId}` | 신규 |
| `POST /attachment` | `POST /attachments` | |
| `GET /attachment/download?attachmentId=` | `GET /attachments/{attachmentId}` | |
| 없음 | `DELETE /attachments/{attachmentId}` | 신규 |
| `POST /guideline` | `POST /guidelines` | |
| 없음 | `GET /guidelines` | 신규 (목록) |
| `GET /guideline/download?guidelineId=` | `GET /guidelines/{guidelineId}` | |
| 없음 | `DELETE /guidelines/{guidelineId}` | 신규 |
| `POST /applicant-list`, `GET /applicant-list/download` | 없음 | 삭제 |

## application

| API | 변경 |
| --- | --- |
| `PATCH /api/application/v11/applicants/{id}/personal` | `photoFileId` 가 숫자에서 문자열(`"photo_…"`)로 바뀌었다. Notion 명세는 롤백해 아직 숫자 예시다 |

## admin (삭제)

- `GET /api/v11/admin/applicants/{applicantId}/admission-ticket`
- `GET /api/v11/admin/applicants/{applicantId}/application-document`

## 내부 gRPC

- `ApplicationService.GetApplicant`: 신규. configuration 이 `applicant_id` 로 지원자 정보와 계정을 조회한다

## 제외 (Notion 비고만 추가, 코드 변경 없음)

- notification `GET /api/notification/v11/notifications/guideline`: 요강 파일 위치 안내만 넣었다
- application 자기소개서·학업계획서 작성: 1600자 설명만 적었다. 1600자는 develop 코드에 이미 있던 값이다
- admin `GET /api/v11/admin/admission-ticket-jobs`: Notion 에만 있던 행이라 `(삭제)` 표시만 했다
- API 공통 규약·에러 코드 규칙: API 가 아닌 규칙 문서다
