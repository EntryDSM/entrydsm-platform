# 관리자 수험표 출력(사진·1차 합격자·한 파일) 작업 컨텍스트

- 이슈: #254
- 브랜치: `bug/254-admission-ticket-print` (`origin/develop` a0259273 에서 분기)
- 대상: admin 내보내기 `ADMISSION_TICKET`, configuration(document) 수험표 렌더링, `contracts/proto/configuration.proto`
- 선행 작업
  - #177 (PR #180) — document 가 수험표를 만든다. 개별 수험표에는 본인 증명사진이 들어간다
  - #195 (PR #197) — 사진·수험표 파일의 오너는 document 다. document 수험표의 수험번호 칸은 받을 길이 없어 `미발급` 이다
  - #163 (PR #166) — `POST /exports` 가 보낸 필터대로 거른다. 필터는 `ADMISSION_TICKET` 에도 적용된다
  - #230 (PR #245) — admin 이 지원자를 application gRPC 로 읽는다. 그 문서 6절이 "admin 수험표 양식 중복 제거 — admin ZIP 이 document 를 부르게 한다"를 미뤄 뒀다. 이번 작업이 그 일이다

개발 전에 현재 구조와 결정 사항을 정리한 문서다. 구현 중에 판단이 바뀌면 이 문서를 먼저 고친다.

## 1. 문제

관리자 화면(지원자 목록)의 "수험표 출력" 버튼에 대한 신고 세 건이다. 원인은 모두 admin
[`ExportJobProcessor.kt`](../../../systems/admin/admin-application/src/main/kotlin/hs/kr/entrydsm/admin/application/ExportJobProcessor.kt)
의 수험표 묶음 한 곳에 있다. 원인은 코드로 확인했다(수정 전 재현은 하지 않았다). 수정 뒤 동작은 8.2 E2E 로 확인했다.

| # | 신고 | 원인 | 근거 |
| --- | --- | --- | --- |
| 1 | 증명사진이 안 들어간다 | admin 은 사진을 가져올 길이 없어 사진 없이 만든다. 사진 칸에 "사진" 글자만 찍힌다 | `ExportJobProcessor.kt:108` `AdmissionTicket.of(applicant, admissionYear)` — `photoDataUri` 기본값 null |
| 2 | 1차 합격자로 등록되지 않은 지원자도 수험표가 나온다 | 프론트가 필터 없이 부르고 백엔드도 전형 상태로 거르지 않는다. 빈 필터는 제출 원서 전체다 | 프론트 `useExportDownloads.ts:16` `createExport({ type })`, `ExportJobProcessor.kt:84` `findAll(job.filter)` |
| 3 | 수험표 파일이 지원자마다 따로 나온다 | 지원자마다 PDF 를 만들어 ZIP 으로 묶는다. 풀면 PDF 가 인원수만큼 나온다 | `ExportJobProcessor.kt:110` `ZipEntry("admission_ticket_….pdf")` |

"수험표 출력" 을 부르는 곳은 관리자 화면 하나다. 프론트(`EntryDSM/EntryDsm-Admission-2026` develop 0545604)의 학생 앱에는 수험표 호출이 없다.

## 2. 현재 구조 (develop a0259273)

### 2.1 버튼에서 파일까지

```text
[entry-admin] pages/ApplicantsList.tsx:175 "수험표 출력"
  → hooks/useExportDownloads.ts:16 createExport({ type: "ADMISSION_TICKET" })     ← filter 없음
  → POST /api/v11/admin/exports → 202 { exportJobId }
      SupportController.createExport → ExportService.create
      → export_job 저장 + ExportJobCreatedEvent(job)      (필터는 DB 에 없고 이벤트의 job 객체로만 넘어간다, SupportJpaEntities.kt:70)
  → 커밋 뒤 @Async ExportJobProcessor.onExportJobCreated → process
      applicantRepository.findAll(job.filter)             ← application ListApplicants + admin screening, 접수번호 순
      bundleAdmissionTickets — 지원자마다
        AdmissionTicketHtml.render(AdmissionTicket.of(applicant, admissionYear))   ← 사진 null
        → OpenHtmlToPdfAdapter.render → ZIP 항목 admission_ticket_{접수번호}.pdf
      → admin 버킷 admission-ticket/admission_tickets_{exportJobId}.zip
  → GET /api/v11/admin/exports/{exportJobId} 를 2초마다, 최대 5분 → COMPLETED 면 presigned downloadUrl 을 새 창으로 연다
```

### 2.2 수험표를 만드는 곳이 두 군데다

| | admin 일괄 `POST /api/v11/admin/exports` (`ADMISSION_TICKET`) | document 개별 `GET /api/document/v11/admission-tickets/{applicantId}` |
| --- | --- | --- |
| 부르는 곳 | 관리자 "수험표 출력" | 프론트에 없다 |
| 대상 | 필터(없으면 제출 원서 전체) | 지목한 1명 (관리자, 본인 학생) |
| 사진 | 없다 | 있다. 원서의 사진 ID 가 그 학생이 올린 사진일 때만 (`FileDocumentService.kt:209`) |
| 수험번호 칸 | admin 발급값, 없으면 `미발급` | 늘 `미발급` |
| 접수 번호 칸 | 있다 | 없다 |
| 전형 상태 확인 | 없다 | 없다 (상태는 admin 만 안다) |
| 양식 | admin `AdmissionTicketHtml` | configuration `AdmissionTicketHtml`. 같은 양식의 사본이라고 ponytail 주석이 있다(`AdmissionTicketHtml.kt:10`) |

### 2.3 필요한 값이 있는 곳

| 값 | 가진 곳 | admin 이 지금 받는가 |
| --- | --- | --- |
| 전형 상태·수험번호·원서 도착 | admin `screening` 테이블 | 가진다 |
| 성명·출신 중학교·지역·전형 | application | `ListApplicants` 로 받는다 |
| 사진 ID·원서 주인 계정 | application (`ApplicantResponse.photo_file_id`·`user_id`) | 응답에는 있지만 admin `Applicant` 로 옮기지 않는다 |
| 사진 파일 | document `files` 행(`public_id` → `object_key`) + configuration 버킷 | 못 받는다. admin 버킷(`STORAGE_BUCKET`)과 다르고, admin 에서 configuration 으로 가는 통로가 없다 |

- admin 의 gRPC 클라이언트는 application(`ApplicationGrpcChannel`)·notification(`NotificationGrpcChannel`) 둘이다
- configuration 은 gRPC 서버(`ConfigurationGrpcServer`, `GRPC_PORT`)를 이미 띄운다. RPC 는 환경변수 다섯 개뿐이다. `//contracts:configuration_java_proto`·`configuration_grpc_java` 타깃도 이미 있다

### 2.4 1차 합격 상태가 붙는 길

- `POST /api/v11/admin/screenings/first/results` (`dryRun: false`) — `ScreeningPolicy` 가 `PENDING` 을 `FIRST_PASS`/`FIRST_FAIL` 로 바꾼다. 원서 미도착·수험번호 없음·총점 없음·지역/전형 없음은 제외한다(상태 그대로). 그래서 산출로 붙은 `FIRST_PASS` 는 모두 수험번호가 있다
- `POST /api/v11/admin/screenings/final/results/{applicantId}` ("2차 합격자 등록") — `FIRST_PASS` 가 아닌 지원자도 받아서 `FINAL_FAIL` 로 만든다(`ScreeningPolicy.evaluateFinal`). 그래서 `FINAL_FAIL` 은 "1차에 합격했었다"는 뜻이 아니다
- `PATCH /api/v11/admin/applicants/{applicantId}/status` (`force: true`) — 흐름을 벗어난 강제 변경이다. 수험번호 없는 `FIRST_PASS` 가 생길 수 있다

### 2.5 사진 크기와 형식

- 증명사진은 jpg·png·webp, 5MB 까지다(`FileCategory.PHOTO`). 프론트는 `image/jpeg, image/png` 만 고르게 하고 줄이지 않고 올린다
- document 수험표는 사진을 원본 그대로 data URI 로 넣는다. 한 장 크기가 사진 크기를 따라간다
- gRPC 기본 수신 한도는 4MB 다. 5MB 사진이 든 수험표 한 장은 기본 설정으로 못 받는다
- webp 는 openhtmltopdf 가 못 읽어 빈 칸이 된다(`FileDocumentService.kt:204` ponytail). 프론트로는 올릴 수 없다
- 서비스 JVM 에는 힙 설정이 없다(`Dockerfile`, bootstrap `BUILD.bazel`). 기본값은 호스트 메모리의 1/4 이고, 한 호스트에 서비스 일곱 개가 뜬다

### 2.6 관련 파일

| 모듈 | 파일 | 바꾼 것 |
| --- | --- | --- |
| contracts | `contracts/proto/configuration.proto` | RPC 추가 (4절) |
| configuration-domain | `document/AdmissionTicketHtml.kt` | 수험번호 값, 접수 번호 칸 |
| configuration-domain | `document/port/in/ApplicantFileUseCase.kt` | 저장하지 않고 PDF 를 돌려주는 `renderAdmissionTicket` |
| configuration-application | `FileDocumentService.kt` | 수험표 렌더와 적재를 나눈다, 사진 축소(`fitTicketPhoto`) |
| configuration-adapter-in | `grpc/ConfigurationGrpcService.kt` | 새 RPC. 예외 → Status 매핑은 application `ApplicationGrpcService.kt:116` 방식을 따랐다 |
| configuration-bootstrap | `application.yaml` | `document.admission-year` 주석만. 빈 설정(`DocumentBeanConfig.kt`)은 그대로다 |
| configuration 테스트 | `FileDocumentServiceTest`, `AdmissionTicketPdfTest`, `DocumentControllerTest`(가짜 구현), `ConfigurationGrpcServiceTest`(새, `grpc_service_test` 타깃) | |
| admin-domain | `model/ApplicantFilter.kt`, `enum/ErrorCode.kt`, `enum/ExportType.kt`, `document/DocumentNaming.kt` | 대상 규칙 `forAdmissionTickets()`, 409 코드, `.zip` → `.pdf` |
| admin-domain | `port/out/PdfRenderPort.kt` → `PdfMergePort.kt`, `port/out/AdmissionTicketPort.kt`(새) | 병합 포트, 수험표 한 장 포트 |
| admin-domain | `document/AdmissionTicketHtml.kt`, `model/AdmissionTicket.kt`, 테스트 `AdmissionTicketHtmlTest.kt`, `BUILD.bazel` 의 `admission_ticket_html_test` | 지웠다 |
| admin-application | `ExportService.kt`, `ExportJobProcessor.kt` | 대상 강제·0명 409, 한 PDF. 처리기의 `admin.admission-year` 주입을 뺐다 |
| admin-application 테스트 | `AdmissionTicketExportTest`(새, `admission_ticket_export_test` 타깃) | 접수와 처리 둘 다 |
| admin-adapter-out | `grpc/ConfigurationGrpcChannel.kt`, `grpc/GrpcAdmissionTicketAdapter.kt`, `document/PdfBoxMergeAdapter.kt` | 새로 만들었다 |
| admin-adapter-out | `document/OpenHtmlToPdfAdapter.kt`, `resources/fonts/NanumGothic-Regular.ttf`, 테스트 `AdmissionTicketPdfTest.kt` | 지웠다. 폰트는 이 어댑터만 썼다 |
| admin-adapter-out | `deps.bzl`, `BUILD.bazel` | configuration proto 두 타깃·pdfbox·protobuf-java 추가, openhtmltopdf 두 개와 빈 `resources` 제거, 테스트 타깃 교체(`pdf_merge_test`, `grpc_admission_ticket_test`) |
| admin-bootstrap | `application.yaml`, `systems/admin/.env.example` | `configuration.grpc.*` 추가, 이제 안 쓰는 `admin.admission-year`·`ADMISSION_YEAR` 삭제 |
| admin-domain | `enum/Region.kt`, `enum/AdmissionType.kt` | `label` 설명의 "수험표" 를 "지원자 목록 엑셀" 로 (admin 은 수험표를 더 그리지 않는다) |

## 3. 결정 (2026-09-21 권장안대로 확정, 구현함)

### 3.1 수험표 한 장은 document 가 만들고 admin 은 이어 붙인다

- admin 이 지원자마다 configuration gRPC `RenderAdmissionTicket(applicant_id, examinee_number)` 를 불러 PDF 한 장을 받는다. 사진과 사진 소유 확인은 document 가 개별 수험표에서 하는 그대로다
- admin 의 양식·렌더러(`AdmissionTicketHtml`, `AdmissionTicket`, `OpenHtmlToPdfAdapter`, 폰트, 테스트 둘)를 지운다. 수험표 양식이 한 벌이 된다
- 택하지 않은 대안 A: admin 이 사진 바이트만 gRPC 로 받아 자기 양식에 넣는다. 고칠 양은 비슷한데 양식 두 벌이 남고, 사진 소유 확인이 admin 에도 필요하다
- 택하지 않은 대안 B: document 가 전원을 한 번에 그려 한 파일로 준다. 수험번호·상태는 admin 만 알아 어차피 목록을 넘겨야 하고, 응답 하나가 인원 × 사진 크기가 된다

### 3.2 대상은 `FIRST_PASS` 만이고 서버가 정한다

- 프론트 필터에 맡기지 않는다. `ExportService.create` 가 `ADMISSION_TICKET` 작업의 `filter.statuses` 를 `{FIRST_PASS}` 로 바꿔 처리기에 넘긴다. 다른 조건(지역·전형 등)은 그대로 적용한다. 규칙은 admin-domain `ApplicantFilter.forAdmissionTickets()` 한 곳에 있다
- `FINAL_PASS`·`FINAL_FAIL` 은 넣지 않는다. 수험표는 1차 발표 뒤 면접 전에 뽑고, `FINAL_FAIL` 은 1차 합격을 뜻하지 않는다(2.4). 단, 2차 합격자 등록을 시작한 뒤 다시 뽑으면 등록한 사람이 빠진다
- 대상이 0명이면 접수 단계에서 409 `ADMISSION_TICKET_NO_TARGET` "수험표를 발급할 1차 합격자가 없습니다." 로 막는다. 프론트는 오류 본문의 `message` 를 토스트로 띄우므로(`apis/http.ts:45`) 프론트를 고칠 필요가 없다. 비동기 실패로 두면 "잠시 후 다시 시도해주세요" 가 떠서 오해를 산다
- 0명 확인에 `ListApplicants` 를 한 번 더 부른다. `create` 트랜잭션 안에서 부른다. 처리기 이벤트가 `AFTER_COMMIT` 이라 트랜잭션을 뺄 수 없고, 목록 한 번이라 DB 연결을 오래 잡지 않는다
- 대상은 수험번호 순으로 뽑는다. 수험번호 없는 `FIRST_PASS`(강제 변경)는 뒤로 보내고 수험번호 칸은 `미발급` 이다

### 3.3 한 파일: PDF 하나, 한 장에 한 명

- 받은 PDF 들을 PDFBox `PDFMergerUtility` 로 이어 붙여 `admission-ticket/admission_tickets_{exportJobId}.pdf`(`application/pdf`)로 올린다. PDFBox 3.x 는 configuration 이 이미 쓴다(`Loader.loadPDF`)
- 한 HTML 에 전원을 넣어 한 번에 그리는 #241 방식은 쓰지 않는다. 모든 사진 data URI 가 한 문자열에 들어간다
- 브라우저가 downloadUrl 을 새 창으로 열면 PDF 뷰어로 보인다. 바로 인쇄할 수 있다
- 병합은 지금 ZIP 처럼 힙에서 한다. 3.4 로 한 장이 작아지는 것이 전제다. 한계는 ponytail 주석으로 남긴다

### 3.4 사진은 칸 크기로 줄여 넣는다 (document)

- 한 파일로 모으면 크기가 한 장 × 인원이다. 예를 들어 5MB 사진 100명이면 500MB 다. 2.5 의 힙과 브라우저·프린터가 감당하기 어렵다
- 사진 칸 폭은 약 66mm 다(A4 본문 170mm × 42% − 안쪽 여백). 가로 600px(약 230dpi)보다 크면 줄여서 JPEG(ImageIO 기본 품질)로 넣는다. 투명 사진은 칸보다 작아도 흰 바탕 JPEG 로 바꾼다. 그대로 두면 투명한 곳에 사진 칸의 회색(#d9d9d9)이 비친다(렌더해 확인, CodeRabbit 리뷰)
- 반씩 줄이는 bilinear 를 쓴다. 12MP 사진에서 `getScaledInstance(SCALE_SMOOTH)` 는 약 450ms, 단계별 bilinear 는 약 35ms 였다(로컬 측정). 150명이면 1분과 5초 차이다
- `ImageIO` 가 못 읽는 사진(webp, 일부 CMYK JPEG)은 줄이지 않고 원본을 넣는다. 지금 동작과 같다
- 개별 수험표 REST 도 같은 코드라 같이 작아진다. 원서(`ApplicationFormPdfAdapter`)는 건드리지 않는다
- 운영 사진 크기는 8.3 SQL 로 볼 수 있다

### 3.5 호출 한도와 실패

- admin → configuration 채널의 `maxInboundMessageSize` 를 16MB 로 올린다. 줄이지 못한 5MB 원본 사진이 든 한 장도 받아야 한다
- 기한은 `CONFIGURATION_GRPC_DEADLINE_MS` 기본 10초다. 한 장에 application 조회, S3 받기, 렌더가 들어간다
- 지원자마다 순서대로 부른다. 한 명당 gRPC 두 단계(admin → configuration → application) + S3 한 번이다. 프론트는 5분까지 기다린다
- 한 명이라도 실패하면 작업을 `FAILED` 로 끝낸다. 실패한 사람을 빼고 뽑으면 빠진 걸 알 수 없다
- 사진을 못 찾거나 남의 사진 ID 가 적힌 지원자는 사진 칸을 비운 채 뽑는다. 개별 수험표와 같은 규칙이다(인적사항 저장에 `photoFileId` 가 필수라 사진 ID 자체가 없는 제출 원서는 없다)

### 3.6 document 수험표 양식

- 수험번호 칸은 넘어온 값을 찍고, 없으면 `미발급` 이다. admin 양식에 있던 `접수 번호` 칸(`ReceiptNumber.of(applicantId)`)을 더해 admin 이 찍던 여섯 칸을 그대로 잇는다
- REST 개별 수험표는 수험번호를 모르니 계속 `미발급` 이다. `AdmissionTicketHtml.kt` 의 양식 중복 ponytail 주석은 지웠고, 수험번호 주석은 REST 경로 이야기로 고쳤다

## 4. gRPC 계약

새 proto 파일을 만들지 않고 configuration 서비스의 기존 계약에 더한다. 서버 등록과 Bazel 타깃이 이미 있다.

```proto
service ConfigurationService {
    // …기존 환경변수 RPC…
    // 관리자 수험표 일괄 출력에 쓰는 수험표 한 장. 저장하지 않고 PDF 를 돌려준다.
    rpc RenderAdmissionTicket(RenderAdmissionTicketRequest) returns (RenderAdmissionTicketResponse);
}

message RenderAdmissionTicketRequest {
    int64 applicant_id = 1;
    // admin 이 발급한 수험번호. 없으면 "미발급" 으로 찍는다.
    optional string examinee_number = 2;
}

message RenderAdmissionTicketResponse {
    // A4 한 장짜리 수험표 PDF. 증명사진이 들어가 수 MB 까지 커질 수 있다.
    bytes pdf = 1;
}
```

| configuration 예외 | Status | admin |
| --- | --- | --- |
| `ApplicantNotFoundException` | `NOT_FOUND` | 작업 실패 |
| `ApplicantLookupFailedException` (application 장애) | `UNAVAILABLE` | 작업 실패 |
| 그 밖 | `INTERNAL` | 작업 실패 |

- `Requester` 권한 확인을 거치지 않는다. gRPC 포트는 compose 안에서만 열린다(`ports` 는 gateway 만 연다)
- admin 은 실패를 작업 실패로만 쓰므로 새 에러 코드가 필요 없다. `NOT_FOUND` 는 `APPLICANT_NOT_FOUND`, 나머지 장애(`UNAVAILABLE`·`DEADLINE_EXCEEDED`·`UNIMPLEMENTED`)는 `ADMISSION_TICKET_GENERATION_FAILED` 로 감싼다. configuration 이 Status 설명에 예외 메시지(`applicantId=…`)를 실어 admin 작업 실패 로그에 남는다

## 5. REST 변화

| API | 지금 | 바뀐 뒤 |
| --- | --- | --- |
| `POST /api/v11/admin/exports` `ADMISSION_TICKET` | 필터대로(없으면 전체), 결과는 ZIP | `FIRST_PASS` 만(다른 조건은 적용), 결과는 PDF 하나. 대상 0명이면 409 `ADMISSION_TICKET_NO_TARGET` |
| `GET /api/v11/admin/exports/{exportJobId}` | `downloadUrl` 이 ZIP | PDF. 응답 모양은 같다 |
| `GET /api/document/v11/admission-tickets/{applicantId}` | 다섯 칸, 사진 원본 | `접수 번호` 칸 추가, 큰 사진은 줄인다. 경로·권한은 그대로 |
| `POST /exports` `APPLICANT_LIST` | | 그대로 |

프론트는 고칠 필요가 없다. 주석·타입 설명의 "ZIP" 만 낡는다(`apis/types.ts:169`, `apis/export.ts:7`, `hooks/useExportDownloads.ts:7·35·82`, `pages/ApplicantsList.tsx:159`). 프론트에 알릴 것은 둘이다. 결과가 PDF 로 새 창에 열리고, 1차 산출 전에는 "1차 합격자가 없습니다" 토스트가 뜬다.

## 6. 하지 않는 것

- 개별 수험표(document REST)의 합격 확인: 프론트가 부르지 않고 수험번호가 늘 `미발급` 이라 수험표로 쓸 수 없다. 학생 화면에 수험표 출력을 붙일 때 admin 상태 확인과 같이 한다
- 한 쪽에 여러 장 모아찍기, 양식(요강 서식) 바꾸기
- webp 디코더
- 렌더 병렬화, 스트리밍 업로드, 임시 파일 병합: 1차 합격자 규모(정원 × `FIRST_PASS_MULTIPLIER` 1.5)에서는 필요 없다
- 원서 PDF 의 사진 축소

## 7. 영향과 배포

- 배포 순서: configuration 을 먼저 올린다. admin 이 먼저 뜨면 새 RPC 가 `UNIMPLEMENTED` 라 수험표 작업만 `FAILED` 가 된다. 다른 admin 기능은 영향이 없다
- admin 새 환경변수: `CONFIGURATION_GRPC_HOST`(기본 `configuration`), `CONFIGURATION_GRPC_PORT`(기본 9090), `CONFIGURATION_GRPC_DEADLINE_MS`(기본 10000). 기본값은 compose 서비스 이름과 configuration `.env.example` 의 `GRPC_PORT` 다. 운영 `.env.configuration` 의 `GRPC_PORT` 가 9090 이 아니면 `.env.admin` 에 넣어야 한다 — 확인 필요
- 명세: `documents/features/applicant-list-excel/api-spec.md`(`type` 설명, 필터 규칙, 409·503 오류)와 `documents/features/admin-applicant-via-grpc/api-spec.md`(ZIP 파일명 행)를 고쳤다. Notion 내보내기 행은 아직이다
- 이미 끝난 ZIP 작업의 downloadUrl 은 그대로 나온다(`object_key` 가 저장돼 있다)
- 서비스 풀 설정이 `spring.hikari` 아래에 있어 Spring Boot 가 읽지 않는다(`spring.datasource.hikari` 여야 한다). 모든 서비스가 같고 이번 범위 밖이라 따로 뺐다

## 8. 검증

### 8.1 단위 (2026-09-21, `//systems/admin/...`·`//systems/configuration/...`·`//contracts/...` 30개 타깃 통과)

- admin `AdmissionTicketExportTest`: 접수 때 보낸 `statuses` 를 버리고 `{FIRST_PASS}` 로 좁히고 다른 조건은 남긴다, 대상 0명(`PENDING`·`FIRST_FAIL`·`FINAL_FAIL` 뿐)이면 409 이고 작업·이벤트가 없다, `APPLICANT_LIST` 는 그대로 접수하고 지원자를 미리 읽지 않는다, 처리기가 수험번호 순(없으면 맨 뒤)으로 받아 PDF 하나로 올린다, 한 장이라도 실패하면 `FAILED` 이고 아무것도 올리지 않는다
- admin `GrpcAdmissionTicketAdapterTest`: 실제 gRPC 서버로 요청 매핑, 6MB 응답 수신(한도), `NOT_FOUND`·`UNAVAILABLE`·`UNIMPLEMENTED` 옮기기. `PdfBoxMergeAdapterTest`: 받은 순서대로 이어 붙인다, 빈 목록은 실패
- configuration `FileDocumentServiceTest`: 개별 수험표의 `접수 번호` 칸, 일괄 출력용은 넘어온 수험번호를 찍고 저장소에 올리지 않는다, 1800×2400 투명 PNG → 600×800 흰 바탕 JPEG, 300×400 불투명 PNG 는 바이트 그대로, 300×400 투명 PNG → 같은 크기 흰 바탕 JPEG. `ConfigurationGrpcServiceTest`: 정상 응답과 Status 매핑
- 일부러 망가뜨려 확인했다: `forAdmissionTickets()` 를 빼면 좁히기·409 테스트 둘이, `maxInboundMessageSize` 를 빼면 6MB 테스트가 실패한다

### 8.2 로컬 E2E (2026-09-21 결과)

application 8121/9121, configuration 8122/9122, admin 8123 + MySQL 3388·Redis 6388·MinIO 9081(`entrydsm-ticketprint-*`). notification 은 띄우지 않았다(admin 은 채널을 늦게 연결해 기동에 필요 없다).

1. 지원자 넷 제출 — 3000×4000 JPEG 4.36MB 사진, 300×400 PNG 사진, 남의 사진 ID, 1차 불합격 예정. 제출에는 자기소개서·학업계획서가, 인적사항 저장에는 `photoFileId` 가 필수다
2. 원서 도착 → 수험번호 발급(100001~100004) → 1차 합격자 없이 `POST /exports` → **409 `ADMISSION_TICKET_NO_TARGET`**
3. 강제 변경으로 1·2·3 `FIRST_PASS`, 4 `FIRST_FAIL` → 필터 없이 `POST /exports` → 약 1.3초 만에 `COMPLETED`
4. 받은 파일은 `application/pdf` **하나**, 60KB, **3쪽**(4번 제외), 수험번호 순. 1쪽 사진은 **600×800 JPEG 14KB**(원본 4.36MB), 2쪽은 PNG 원본, 3쪽은 사진 칸이 비었다(남의 사진). 쪽마다 수험번호·성명·접수 번호(0001~0003)를 확인했고 이미지로 렌더해 사진 방향·위치를 눈으로 봤다
5. document 개별 수험표 REST(관리자)는 그대로 200, 수험번호 `미발급`·접수 번호 `0001`·줄인 사진
6. configuration 을 끄고 다시 → **`FAILED`**, admin 로그 "Export job failed … 수험표 생성에 실패했습니다."

### 8.3 운영 사진 크기 (configuration_db, 읽기 전용)

```sql
SELECT content_type,
       COUNT(*)                     AS cnt,
       ROUND(AVG(size_bytes) / 1024) AS avg_kb,
       ROUND(MAX(size_bytes) / 1024) AS max_kb
FROM files
WHERE object_key LIKE 'dsm_Entry/Backend/photo/%'
GROUP BY content_type;
```

## 9. 정한 것 (2026-09-21 "권장안대로 진행")

| # | 질문 | 고른 것 | 고르지 않은 것 |
| --- | --- | --- | --- |
| 1 | 수험표 대상 상태 | `FIRST_PASS` 만 | `FIRST_PASS` + `FINAL_PASS` — 2차 등록을 시작한 뒤 다시 뽑아도 합격자가 남는다 |
| 2 | 사진 축소 | 한다 (3.4) | 원본 그대로 |
| 3 | 사진을 넣는 방식 | document 가 한 장씩 만든다 (3.1) | admin 이 사진만 받는다 (대안 A) |
| 4 | 개별 수험표 REST 의 합격 확인 | 이번엔 하지 않는다 (6절) | 학생(OWNER) 다운로드 권한을 뺀다 |
