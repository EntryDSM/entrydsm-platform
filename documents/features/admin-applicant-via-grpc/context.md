# admin 지원자 데이터를 application gRPC 조회로 바꾸는 작업 컨텍스트

- 이슈: 미정
- 브랜치: `fix/admin-applicant-via-grpc` (`origin/develop` c8af1600 에서 분기)
- 대상: admin 지원자·전형·수험번호·통계·내보내기 전부, `contracts/proto/application.proto`, application gRPC 서버
- 선행 작업
  - #145 — 같은 방향의 구현. base 였던 PR #143 이 머지 없이 닫혀 유실됐다. 어느 브랜치에도 남아 있지 않다
  - #195 (PR #197, 병합) — 원서·수험표 파일을 `applicantId` 로 찾고 본인 판정을 application gRPC `GetApplicant` 에 맡기기로 했다. 그 문서의 3절이 "admin 지원자 데이터 공백과 함께 풀어야 한다"고 적어 둔 그 작업이다
  - #216 (작업 중) — 원서 생성 API 가 기존 원서 ID 를 돌려줘, `applicantId` 가 지원자에게 항상 주어지는 안정된 키가 됐다

개발 전에 현재 구조와 결정 사항을 정리한 문서다. 구현 중에 판단이 바뀌면 이 문서를 먼저 고친다.

## 1. 문제

`admin_db.applicant` 에 행을 넣는 코드가 develop 에 없다. 확인한 근거는 셋이다.

- `INSERT INTO applicant` 이 SQL·Kotlin 어디에도 없고, 마이그레이션에 시드도 없다
- admin 의 모든 쓰기는 read-modify-write 다. `ApplicantService`·`ScreeningService`·`ScorePolicyService` 의 `save`/`saveAll` 은 전부 `requireApplicant()` 나 `findAll()` 로 기존 행을 읽은 뒤 갱신한다
- admin 에 application 을 부르는 통로가 없다. gRPC 클라이언트는 notification 하나뿐이고(`NotificationGrpcChannel`) 이벤트 컨슈머도 없다

그래서 admin 의 지원자 기능이 전부 빈 결과를 낸다. 목록·상세·원서 도착·상태 변경·수험번호 발급·통계·1차/최종 전형·엑셀·수험표 ZIP.

엔티티 주석([`ApplicantJpaEntity.kt:20`](../../../systems/admin/admin-adapter-out/src/main/kotlin/hs/kr/entrydsm/admin/adapterout/entity/ApplicantJpaEntity.kt))에 이미 적혀 있다 — "원서 접수의 원본 데이터는 application 시스템이 갖는 것이 맞다. 그 시스템이 생기면 이 테이블은 조회 전용 투영으로 바꾸거나 gRPC 조회로 대체한다."

라우팅·인증은 원인이 아니다. 게이트웨이 `ADMIN` 경로 접두사(`/api/v11/admin`)와 컨트롤러 경로가 맞고, 게이트웨이가 넣는 `X-User-Role` 과 admin 이 비교하는 `"ADMIN"`, identity `Role.ADMIN` 이 모두 같은 문자열이다.

## 2. 현재 구조 (develop c8af1600)

### 2.1 admin 지원자 조회 흐름

```text
GET /api/v11/admin/applicants
  ApplicantController.search                    (admin-adapter-in)
  → ReadApplicantUseCase = ApplicantService     (admin-application)
  → ApplicantRepository = ApplicantPersistenceAdapter
  → ApplicantJpaRepository + ApplicantSpecifications → admin_db.applicant  ← 비어 있다
```

### 2.2 관련 파일

| 모듈 | 파일 | 역할 |
| --- | --- | --- |
| contracts | `contracts/proto/application.proto` | `CreateApplication`, `GetApplication`, `CancelApplication`, `GetApplicant`. **목록 RPC 가 없다** |
| application-adapter-in | `grpc/ApplicationGrpcService.kt` | gRPC 서버 구현 |
| application-application | `service/ApplicationCommandService.kt` | `findApplicant(applicantId)` → `ApplicantResult` |
| admin-domain | `model/Applicant.kt` | `receiptNumber`·인적사항·`examineeNumber`·`isSubmitted`·`status`·`score` 를 한 덩어리로 가진 모델 |
| admin-domain | `model/ApplicantFilter.kt` | `keyword`(이름·수험번호), `regions`, `admissionTypes`, `graduationStatuses`, `isSubmitted`, `statuses` |
| admin-domain | `port/out/AdminRepositories.kt` | `ApplicantRepository` (`search`·`findAll`·`findById`·`save`·`saveAll`) |
| admin-domain | `policy/ExamineeNumberPolicy.kt` | 원본 도착자에게 접수번호 순으로 100001 부터 발급 |
| admin-domain | `policy/ScreeningPolicy.kt` | 지역×전형 묶음 안에서 총점 내림차순, 동점은 접수번호 우선 |
| admin-domain | `model/AdmissionTicket.kt` | 수험표 인쇄 값 (접수번호·수험번호·성명·학교·지역·전형) |
| admin-application | `ApplicantService.kt`, `ScreeningService.kt`, `StatisticsService.kt`, `ScorePolicyService.kt`, `ExportJobProcessor.kt` | 모두 `ApplicantRepository` 를 읽는다 |
| admin-adapter-out | `persistence/ApplicantPersistenceAdapter.kt`, `repository/ApplicantSpecifications.kt`, `entity/ApplicantJpaEntity.kt` | JPA 구현. 이번에 지운다 |
| admin-bootstrap | `db/migration/V001__create_admin_tables.sql` | `applicant` 테이블 |

### 2.3 양쪽이 가진 데이터

| admin `Applicant` | application 에 있는가 | 비고 |
| --- | --- | --- |
| `receiptNumber` | **없다** | application 에 접수번호 개념이 없다 |
| `name`, `birthDate`, `phoneNumber` | `applicants.name` / `birthdate` / `phone_number` | 전부 nullable |
| `region` | `applicants.region` | `DAEJEON` / `NATIONWIDE` ↔ `DAEJEON` / `NATIONAL` |
| `admissionType` | `applicants.admission_type` | `GENERAL` / `MEISTER` / `SOCIAL` ↔ `REGULAR` / `MEISTER` / `SOCIAL` |
| `graduationStatus` | `applicants.graduation_type` | `EXPECTED` / `GRADUATED` / `GED` ↔ `PROSPECTIVE` / `GRADUATED` / `GED` |
| `schoolName` | `middle_school_infos.school_name` | |
| `score.totalScore` | `applicants.total_score` | application 이 `ScoreCalculator` 로 산출해 저장한다 |
| `score.subjectScore`·`attendanceScore`·`volunteerScore` | 계산 중에만 존재 | `ScoreCalculator` 안에서 쓰고 버린다. 저장하지 않는다 |
| `examineeNumber` | 없다 | admin 고유 |
| `isSubmitted` (원서 원본 **우편 도착**) | 없다 | admin 고유. application 의 제출과 다른 개념이다 |
| `status` (PENDING·1차·최종) | 없다 | application `ApplicantStatus`(DRAFT·SUBMITTED·…)와 다른 축이다 |
| `submittedAt` | 이름만 같고 뜻이 다르다 | admin 은 원본 도착 시각(`updateArrival` 이 채운다), application 은 원서 제출 시각 |

성적 모델이 서로 다르다. application 의 `ScoreCalculator` 는 교과(80점 환산)·출결(15)·봉사(15)·가산점을 전형별 상한(일반 173, 특별 119)에 맞춰 계산한다. admin 의 `score_policy` 는 항목별 가중치를 곱해 총점을 다시 만드는 별개 모델이고, 곱할 항목 점수를 만드는 코드가 없다.

제출 검증(`ApplicationCommandService.submit`)이 요구하는 것은 `admissionType`·`name`·`guardianName`·`introduction`·`studyPlan` 뿐이다. **제출된 원서에도 `region`·`graduationType`·`birthdate`·`phoneNumber`·중학교·`totalScore` 가 비어 있을 수 있다.**

## 3. 결정

### 3.1 데이터 소유를 나눈다

- **application**: 원서 내용 전부 — 인적사항, 지역, 전형, 학력, 중학교, 총점, 원서 제출 상태·시각
- **admin**: 전형 진행만 — 수험번호, 원서 원본 도착 여부·시각, 전형 상태(PENDING·1차·최종)

admin 은 요청마다 application gRPC 로 지원자를 읽어 자기 행과 합쳐 응답한다. 인적사항을 복제하지 않는다.

### 3.2 접수번호를 없애고 `applicantId` 를 쓴다

`applicants.id` 는 AUTO_INCREMENT 라 "접수 순서대로 부여되는 번호"라는 접수번호의 정의를 이미 만족한다. #195 가 문서 경로 키로, #216 이 원서 생성 응답으로 같은 값을 쓴다.

- `Applicant.receiptNumber` 를 지우고 `id`(= application `applicantId`)로 대체한다. `id` 는 nullable 이 아니다
- 수험번호 발급 순서, 전형 동점 처리, 엑셀 정렬 기준을 `applicantId` 오름차순으로 바꾼다
- 엑셀 헤더 "접수번호"는 그대로 두고 값만 `applicantId` 로 채운다. 아무도 이전 값을 본 적이 없다(행이 없었다)
- 원서를 만들고 제출하지 않은 지원자가 번호를 하나 먹어 번호가 띄엄띄엄해진다. 접수번호는 순서만 나타내면 되므로 받아들인다

### 3.3 성적은 application 총점만 쓴다

admin 의 항목별 점수(교과·출결·봉사)와 `score_policy` 가중치 재계산을 없앤다.

- 항목 점수를 만드는 코드가 없어 지금도 항상 비어 있다
- 살리려면 application 이 이미 계산한 값을 저장·노출하고 admin 이 **다른 가중치로 다시 곱해야** 한다. 전형 상한(173/119)과 어긋난 총점이 나와 합격자 산출이 틀어진다. 점수 규칙의 주인은 application 하나여야 한다
- `GET/PATCH /api/v11/admin/score-policy` 는 API 와 `score_policy` 테이블을 그대로 두되, 아무 지원자도 다시 계산하지 않으므로 `recalculate` 파라미터를 지운다
- 지원자 응답의 `score` 는 `{ "totalScore": ... }` 한 필드만 남는다. 엑셀에서 "교과 점수"·"출결 점수"·"봉사 점수" 열을 뺀다. 도메인의 `ApplicantScore` 를 지우고 `Applicant.totalScore` 로 둔다

### 3.4 gRPC 계약

기존 `ApplicantResponse` 에 admin 이 필요한 필드를 더하고, 목록 RPC 를 추가한다. 필드 추가는 지금 `GetApplicant` 를 쓰는 configuration 에 영향이 없다.

```proto
service ApplicationService {
    // ...
    rpc GetApplicant(GetApplicantRequest) returns (ApplicantResponse);
    rpc ListApplicants(ListApplicantsRequest) returns (ListApplicantsResponse);
}

message ApplicantResponse {
    reserved 6;
    int64 user_id = 1;
    optional string name = 2;
    optional string school_name = 3;
    Region region = 4;
    AdmissionType admission_type = 5;
    int64 applicant_id = 7;
    optional string photo_file_id = 8;
    // admin 이 쓰는 값. 원서가 작성 중이면 비어 있다
    optional string birthdate = 9;          // ISO-8601 (yyyy-MM-dd)
    optional string phone_number = 10;
    GraduationType graduation_type = 11;
    optional double total_score = 12;
    ApplicantStatus applicant_status = 13;
    optional int64 submitted_at_epoch_millis = 14;
}

// 제출된 원서 전체를 준다. 필터·페이징은 admin 이 자기 행과 합친 뒤에 해야 한다
message ListApplicantsRequest {}

message ListApplicantsResponse {
    repeated ApplicantResponse applicants = 1;
}

enum GraduationType {
    GRADUATION_TYPE_UNSPECIFIED = 0;
    GRADUATION_TYPE_PROSPECTIVE = 1;
    GRADUATION_TYPE_GRADUATED = 2;
    GRADUATION_TYPE_GED = 3;
}
```

- `ListApplicants` 는 `status` 가 `SUBMITTED`·`REVIEWING`·`COMPLETED` 인 원서만 준다. 작성 중(`DRAFT`)과 취소(`CANCELED`)는 지원자가 아니다
- `birthdate` 를 문자열로 두는 것은 proto 에 날짜 타입이 없고 epoch 로 바꾸면 시간대가 끼어들기 때문이다. 시각인 `submitted_at` 만 epoch millis 다

### 3.5 admin 스키마 (V003)

`applicant` 를 전형 정보만 남긴 `screening` 으로 바꾼다.

```sql
CREATE TABLE screening (
    applicant_id    BIGINT      NOT NULL,   -- application applicants.id. AUTO_INCREMENT 아님
    examinee_number VARCHAR(20) NULL,
    is_arrived      BIT(1)      NOT NULL,   -- 원서 원본(우편) 도착
    status          VARCHAR(20) NOT NULL,
    arrived_at      DATETIME(6) NULL,
    updated_at      DATETIME(6) NULL,
    PRIMARY KEY (applicant_id),
    KEY idx_screening_status (status)
);

DROP TABLE applicant;
```

- `is_submitted`/`submitted_at` 을 `is_arrived`/`arrived_at` 으로 바꾼다. 제출 시각은 이제 application 이 갖는다
- `applicant_id` 는 admin 이 넣는 값이다. 처음 손대는 지원자의 행은 그때 만든다(도착 표시·상태 변경·수험번호 발급). 행이 없으면 `PENDING`·미도착·수험번호 없음으로 본다
- 운영 데이터는 0행일 것이다(넣는 코드가 없었다). 배포 전에 `SELECT COUNT(*) FROM applicant;` 로 확인하고, 0 이 아니면 옮길 값을 따로 정한다
- `score_policy`·`admission_quota`·`export_job`·`notice`·`question_answer` 는 건드리지 않는다
- `admin-bootstrap/src/main/resources/schema.sql` 을 지운다. Flyway 도입(#111) 뒤로 실행되지 않는 죽은 파일이고(`spring.sql.init.mode` 설정이 없다) 주석도 사실과 다른데, 지운 `applicant` 테이블을 그대로 담고 있어 남겨 두면 혼란만 준다

### 3.6 조회·필터·페이징은 admin 이 메모리에서 한다

`GrpcApplicantDataAdapter` 가 `ApplicantRepository` 를 구현한다. `ListApplicants` 로 받은 목록에 `screening` 행을 왼쪽 조인해 admin `Applicant` 를 만들고, 필터·정렬·페이징을 메모리에서 적용한다.

한 회차 수천 명 규모다. `StatisticsService` 가 이미 같은 이유로 `findAll()` + 메모리 집계를 쓴다(그쪽 ponytail 주석과 같은 한계). 어댑터에 ponytail 주석으로 천장과 올라갈 길(application 에 필터·페이징 RPC 를 넘김)을 적는다.

- `keyword` 는 이름(application)과 수험번호(admin) 양쪽을 봐야 해서 어차피 합친 뒤에만 걸 수 있다
- 정렬 기본값은 `applicantId` 오름차순
- 캐시는 두지 않는다

### 3.7 비어 있는 값과 장애

- admin `Applicant` 의 `name`·`birthDate`·`phoneNumber`·`region`·`admissionType`·`graduationStatus`·`schoolName` 을 nullable 로 바꾼다. 제출 검증이 이들을 요구하지 않아 비어 있을 수 있다(2.3). 목록·상세·엑셀은 빈 값으로 찍고 500 을 내지 않는다
- `ScreeningPolicy.isEvaluable` 에 `region != null && admissionType != null` 을 더한다. 지역×전형으로 묶을 수 없는 지원자는 지금처럼 `excluded` 로 빠진다
- 통계의 지역·전형 분포도 값이 빈 원서를 뺀다(넣을 칸이 없다). `APPLICANT_COUNT.total` 에는 그대로 든다
- application 호출 실패는 503 `APPLICATION_SERVICE_UNAVAILABLE` 로 내보낸다. identity·configuration 과 같은 이름이다(#195 2.6)
- 호출 제한 시간은 configuration·identity 와 같은 `APPLICATION_GRPC_DEADLINE_MS` 기본 3000ms. 목록은 건수가 많으니 별도 값(`APPLICATION_GRPC_LIST_DEADLINE_MS`, 기본 10000ms)을 둔다
- admin `.env.example` 에 `APPLICATION_GRPC_HOST`·`PORT`·두 deadline 을 더한다

## 4. admin REST 응답 변화

경로와 HTTP 규약은 그대로다. 필드만 바뀐다.

| 필드 | 변화 |
| --- | --- |
| `applicantId` | 그대로. 값이 application `applicantId` 가 된다 |
| `receiptNumber` | **삭제** |
| `isSubmitted` | `isArrived` 로 이름을 바꾼다. 뜻(원본 우편 도착)은 같다 |
| `submittedAt` | 뜻이 **원서 제출 시각**(application)으로 바뀐다 |
| `arrivedAt` | **추가**. 이전 `submittedAt` 의 뜻(원본 도착 시각) |
| `name`·`birthDate`·`phoneNumber`·`region`·`admissionType`·`graduationStatus`·`schoolName` | nullable |
| `score` | `{ "totalScore": ... }` 만 남는다 |
| `PATCH /applicants/{id}/arrival` 요청 `isSubmitted` | `isArrived` 로 이름을 바꾼다 |
| 통계 `dailyTrend` | 집계 기준이 원본 도착일에서 **원서 제출일**로 바뀐다. "일별 지원자 추이"의 뜻에 맞다 |
| 엑셀 열 | "교과 점수"·"출결 점수"·"봉사 점수" 삭제, "원서 도착" 값은 그대로 |
| 수험표 ZIP 파일명 | `admission_ticket_{applicantId}.pdf` |
| `PATCH /score-policy` 요청 `recalculate` | **삭제** |

## 5. 영향과 배포

- application 을 먼저 배포하고 `/actuator/health/readiness` 를 확인한 뒤 admin 을 배포한다. 새 admin 이 먼저 뜨면 `ListApplicants` 가 `UNIMPLEMENTED` 로 실패해 지원자 기능이 전부 503 이 된다
- 자동 배포(`deploy.yml`)는 호스트마다 `docker compose up -d` 로 한꺼번에 띄워 순서를 보장하지 않는다. #195 때와 같이 호스트마다 `docker compose up -d application` 을 먼저 하고 준비를 확인한 뒤 나머지를 올린다
- 관리자 화면: `receiptNumber` 가 없어지고 `isSubmitted` → `isArrived`, `score` 가 총점만 남는다. 개별 수험표·원서 원본은 #195 대로 document 를 같은 `applicantId` 로 부른다
- 롤백: V003 이 `applicant` 를 지우므로 이전 admin 은 `ddl-auto: validate` 에서 기동하지 못한다. 되돌리려면 V001 의 `applicant` DDL 을 다시 만들고 `flyway_schema_history` 에서 V003 행을 지운다. 원본 도착·수험번호·전형 상태는 `screening` 에 남아 있어 옮길 수 있다
- admin 이 application 에 의존하게 되므로 application 장애가 admin 지원자 기능 전체로 번진다. 지금은 빈 결과라 어차피 못 쓰는 기능이고, 데이터 중복을 없애는 값이 더 크다고 본다

## 6. 하지 않는 것

- **제출 검증 강화**: 제출된 원서에 지역·학력·중학교·성적이 비어 있을 수 있는 건 application 쪽 문제다. 이 작업은 빈 값을 견디게만 하고, 검증은 따로 이슈를 낸다
- **수험번호를 application 이나 document 로 넘기기**: #195 가 document 수험표에 `미발급` 으로 찍기로 했다. 발급 대상 데이터가 생긴 뒤에 다시 본다
- **admin 수험표 양식 중복 제거**: admin 일괄 ZIP 과 configuration 개별 수험표가 양식을 따로 갖는다. 지원자 데이터가 붙은 뒤 admin ZIP 이 document 를 부르게 한다
- **이벤트 투영**: Redis Stream(`application.applicant-status`)으로 admin 에 복제하는 길도 있지만, 스키마 두 벌을 계속 맞춰야 하고 지금 스트림에는 원서 내용이 없다
- **application 에 필터·페이징 RPC**: 규모가 커지면 그때 넘긴다 (3.6)

## 7. 검증 계획

- `bazel test` admin·application 통과
- 로컬 E2E (MySQL 8.4·Redis 컨테이너, `bazel-bin` 바이너리 직접 실행)
  - 빈 DB 에 admin V001~V003 적용 후 스키마 검증 통과
  - 원서를 만들어 제출한 계정 2~3개를 application 에 만들고, admin 목록에 그대로 보이는지
  - 필터: 지역·전형·학력(application 값), 원본 도착·전형 상태(admin 값), 이름·수험번호 키워드, 페이징 경계
  - 원본 도착 표시 → `screening` 행 생성 → 수험번호 일괄 발급 → 1차 전형 → 최종 전형 순서
  - 인적사항이 덜 채워진 제출 원서가 목록·상세·엑셀에서 500 없이 빈 값으로 나오는지, 전형에서 `excluded` 로 빠지는지
  - 엑셀·수험표 ZIP 에 실제 지원자가 들어가는지
  - application 을 끈 뒤 지원자 API 가 503 `APPLICATION_SERVICE_UNAVAILABLE` 인지, 공지·QnA·내보내기 목록은 200 인지
