# 원서를 요강 <서식 1> 양식으로 서버가 만드는 작업 컨텍스트

- 이슈: 미정
- 브랜치: 미정 (`origin/develop` c8af1600 에서 분기)
- 대상: `contracts/proto/application.proto`, application gRPC 서버, configuration 의 document(원서 파일) 전부
- 근거 문서: 2027학년도 대덕소프트웨어마이스터고등학교 신입생 입학전형 요강 15쪽 `<서식 1> (인터넷접수 후 출력)`
- 선행 작업
  - #195 (PR #197, 병합) — 원서·수험표 파일을 `applicantId` 로 찾고 본인 판정을 application gRPC `GetApplicant` 에 맡기기로 했다
  - #216 — 원서 생성 API 가 기존 원서 ID 를 돌려줘 `applicantId` 가 안정된 키가 됐다
  - `admin-applicant-via-grpc` (작업 중) — 같은 proto 의 `ApplicantResponse` 를 9~14 번으로 늘리고 `ListApplicants` 를 더하는 중이다. **필드 번호가 겹치지 않도록 이 작업은 새 message 만 더한다**

개발 전에 현재 구조와 결정 사항을 정리한 문서다. 구현 중에 판단이 바뀌면 이 문서를 먼저 고친다.

## 1. 문제

요강의 원서는 `<서식 1> (인터넷접수 후 출력)` 이다. 지원자가 온라인으로 접수한 뒤 **그 내용이 채워진 원서를 출력**해서 서명·날인을 받아 우편으로 보내는 종이다.

지금 코드의 원서는 그 종이가 아니다. 학생이 아무 pdf·hwp 나 올리는 **업로드 파일**이다.

- `PUT /api/document/v11/applications/{applicantId}` 로 파일을 받는다 ([ApplicantFileController.kt:28](../../../systems/configuration/configuration-adapter-in/src/main/kotlin/hs/kr/entrydsm/configuration/adapterin/document/ApplicantFileController.kt))
- `FileCategory.APPLICATION` 의 `storers` 가 `{ADMIN, OWNER}` 다 ([FileCategory.kt:22](../../../systems/configuration/configuration-domain/src/main/kotlin/hs/kr/entrydsm/configuration/domain/document/FileCategory.kt))
- 서버는 원서 내용을 한 글자도 찍지 않는다. 양식이 맞는지 확인할 방법도 없다

반면 **수험표는 이미 서버가 만든다.** application gRPC 로 지원자를 읽고, XHTML 을 만들고, openhtmltopdf 로 PDF 를 찍어 S3 에 올린다 ([FileDocumentService.kt:58](../../../systems/configuration/configuration-application/src/main/kotlin/hs/kr/entrydsm/configuration/application/FileDocumentService.kt)).

즉 이 작업은 **없는 기능을 새로 만드는 것이 아니라, 수험표가 이미 간 길을 원서가 똑같이 가는 것**이다. 구조는 그대로 두고 두 가지만 하면 된다.

1. 서식 1 을 그리는 렌더러를 만든다
2. 서식 1 의 칸을 채울 값을 application 에서 gRPC 로 가져온다 — **이 작업 부피의 대부분이다**

## 2. 현재 구조 (develop c8af1600)

### 2.1 수험표 생성 흐름 (원서가 따라갈 길)

```text
GET /api/document/v11/admission-tickets/{applicantId}
  ConfigurationAuthorizationInterceptor           X-User-Id·X-User-Role → Requester
  → ApplicantFileController.generateAdmissionTicket
  → FileDocumentService.generateAdmissionTicket
      ├ requireApplicant → ApplicantPort.findById → gRPC GetApplicant   (본인 판정)
      ├ photoDataUri(photoFileId)  → S3 → base64 data URI
      ├ AdmissionTicketHtml.render → XHTML
      ├ PdfRenderPort.render       → OpenHtmlToPdfAdapter (openhtmltopdf 1.1.73)
      └ store(...)                 → 서명 URL 발급 → S3 업로드 → files 행 저장
  → FileResponse.ofApplicant
```

### 2.2 원서(업로드) 흐름 — 이번에 없어진다

```text
PUT  /api/document/v11/applications/{applicantId}   멀티파트 pdf·hwp, 10MB
GET  /api/document/v11/applications/{applicantId}   올린 게 없으면 { "exists": false }
```

### 2.3 관련 파일

| 모듈 | 파일 | 역할 |
| --- | --- | --- |
| contracts | `contracts/proto/application.proto` | `GetApplicant` 가 주는 `ApplicantResponse` 는 13 필드다. 서식 1 에 모자란다 |
| application-adapter-in | `grpc/ApplicationGrpcService.kt` | gRPC 서버 |
| application-application | `service/ApplicationCommandService.kt` | `findApplicant` → `ApplicantResult` 매핑 |
| application-domain | `model/Applicant.kt` | 원서 내용 전부를 가진 집합체. 서식 1 이 필요한 값이 거의 다 여기 있다 |
| application-domain | `service/ScoreCalculator.kt` | 반영 학기 선택 규칙(자유학기 건너뛰기)이 여기 있다 |
| configuration-domain | `document/FileCategory.kt` | 파일 종류별 권한표. `storers = emptySet()` 이 "서버가 만든다"는 뜻이다 |
| configuration-domain | `document/AdmissionTicketHtml.kt` | 수험표 XHTML. 서식 1 렌더러가 따라 쓸 본 |
| configuration-domain | `document/Applicant.kt` | document 가 쓰는 지원자 모델. 지금 6 필드 |
| configuration-domain | `document/port/out/ApplicantPort.kt` | `fun interface` 라 메서드를 더하면 SAM 이 깨진다 |
| configuration-application | `FileDocumentService.kt` | 적재·생성·조회 전부 |
| configuration-adapter-out | `GrpcApplicantAdapter.kt` | gRPC 클라이언트 |
| configuration-adapter-out | `OpenHtmlToPdfAdapter.kt` | XHTML → PDF. 폰트 등록 |

### 2.4 서식 1 의 칸과 데이터

요강 15쪽을 `pdftotext -layout` 으로 읽어 칸을 전수 대조했다.

| 서식 1 칸 | application 에 있는가 | 지금 gRPC 로 오는가 |
| --- | --- | --- |
| 접수번호 | `applicants.id` (= `applicantId`) | 예 |
| 학교코드 | **없다**. `institution_codes.code` 는 학교 이름 검색용 조회표이고 원서에 저장되지 않는다 | — |
| 수험번호 | 서식이 `*기재하지 않음` 이라 비운다 | — |
| 성명·전화번호·생년월일 | `name`·`phone_number`·`birthdate` | 예 |
| 출신지역 | **없다**. `region` 은 아래 "지역" 칸(대전/전국)이지 출신지가 아니다 | — |
| 성별 | `gender` | 아니오 |
| 출신학교 | `middle_school_infos.school_name` | 예 |
| 졸업구분 | `graduation_type` | 예 |
| 졸업년월 | `graduation_date` | 아니오 |
| 주소 | `zip_code`·`address_base`·`address_detail` | 아니오 |
| 사진(3cm×4cm) | `photo_file_id` → document PHOTO | 예 (수험표가 이미 박는다) |
| 보호자 성명·관계·휴대전화 | `guardian_name`·`guardian_relation`·`guardian_phone_number` | 아니오 |
| 지역 | `region` | 예 |
| 전형유형 | `admission_type` | 예 |
| 특기사항 | `special_admission_type` (`NONE`·`NATIONAL_MERIT`·`SPECIAL_ADMISSION`) | 아니오 |
| 교과성적(성취도) 7과목 × 4열 | `subject_grades` (학기별 7과목, `A`~`E`·`X`) | 아니오 |
| 미인정 결석·지각·조퇴·결과 | `academic_records` 4 컬럼 | 아니오 |
| 봉사활동 시간 | `academic_records.volunteer_time` | 아니오 |
| 가산점 DSM알고리즘대회·정보처리기능사 | `is_dsm_algorithm_awarded`·`is_programming_certified` | 아니오 |
| 보훈번호 | **없다**. `SpecialAdmissionType.NATIONAL_MERIT` 플래그만 있고 번호는 어디에도 없다 | — |
| 원서작성자 교사·연락처 | `middle_school_infos.teacher_name`·`school_phone` | 아니오 |
| 서명·날인·추천서(중학교장 직인) | 인쇄 후 수기 | — |

**결론: 서식 1 이 요구하는 값의 대부분은 application DB 에 이미 있고, gRPC 계약에만 없다.** 없는 건 학교코드·출신지역·보훈번호 셋뿐이다.

### 2.5 제출된 원서에 빈 칸이 생기는 범위

`submit` 이 요구하는 것은 다섯뿐이다 — `admissionType`·`name`·`guardianName`·`introduction`·`studyPlan` ([ApplicationCommandService.kt](../../../systems/application/application-application/src/main/kotlin/hs/kr/entrydsm/application/application/service/ApplicationCommandService.kt)).

다만 인적사항·가족·전형은 **한 요청에서 non-null 로 함께 저장**되므로(`updatePersonal`·`updateFamily`·`updateType`), `name` 과 `guardianName` 과 `admissionType` 이 있으면 전화번호·생년월일·성별·사진·주소·보호자 관계/휴대전화·지역·졸업구분·(검정고시가 아니면)졸업년월도 이미 차 있다.

**제출본에서 실제로 빌 수 있는 칸은 중학교 정보(출신학교·교사·연락처), 교과성적, 출결, 봉사시간, 가산점뿐이다.** 성적 입력 API(`/api/evaluation/v11/evaluations/...`)를 거치지 않고 제출할 수 있기 때문이다.

### 2.6 교과성적 표의 열 ↔ 학기

요강 6·7쪽이 반영 학기를 정한다.

- 졸업예정자: 3학년 1학기(40점) + 자유학기가 아닌 직전 2개 학기(각 20점) = **3개 학기**
- 졸업자: 3학년 2학기부터 자유학기가 아닌 **4개 학기**(각 20점)

`ScoreCalculator` 가 이미 같은 규칙을 쓴다 — 졸업예정자는 3-1 고정 뒤 `2-2 → 2-1 → 1-2 → 1-1` 순으로 성적 있는 2개, 졸업자는 `3-2 → 3-1 → 2-2 → 2-1 → 1-2 → 1-1` 순으로 4개.

서식의 열 이름 `3학년 2학기 / 3학년 1학기 / 직전학기 / 직전전학기` 는 **절대 학기가 아니라 자유학기를 건너뛴 상대 순서**다. 졸업예정자는 3학년 2학기 열이 빈다.

### 2.7 렌더링 가능성 (실측)

서식 1 을 근사한 XHTML 을 저장소가 쓰는 openhtmltopdf 1.1.73 으로 직접 렌더해 확인했다. `rowspan`·`colspan`·회색 헤더·표 3단 스택이 A4 한 장에 정상으로 나온다.

실측으로 걸린 것 하나 — 요강의 `기술∙가정` 은 `∙`(U+2219)인데 `NanumGothic-Regular.ttf` 에 글리프가 없어 `#` 로 찍힌다. **`기술·가정`(U+00B7)을 써야 한다.** 전각 공백(U+3000)도 글리프가 없어 `&#160;` 를 쓴다.

## 3. 결정

### 3.1 원서를 업로드에서 서버 생성으로 바꾼다

`FileCategory.APPLICATION` 의 `storers` 를 비우고(`emptySet()`), `PUT /applications/{applicantId}` 를 지운다. `GET /applications/{applicantId}` 는 **`GET /applications` 로 바꾸고** 동작도 "요청할 때마다 서식 1 로 새로 만들어 준다"로 바꾼다. 응답은 수험표와 같은 `FileResponse` 다.

- 서식 1 은 "인터넷접수 후 출력"이다. 학생이 올릴 파일이 아니라 시스템이 내줄 파일이다
- 경로와 응답이 함께 바뀐다. 프론트는 조회 경로에서 `applicantId` 를 빼고 `exists` 분기를 지운다 (5절)
- 업로드 화면은 프론트에서 없어져야 한다 (5절)
- `FileNaming.applicationFileName` 에서 확장자 인자를 지운다. 생성물은 PDF 뿐이라 pdf·hwp 중 최근 것을 고르던 `findApplication` 의 분기도 사라진다

두 벌(업로드본 + 생성본)을 함께 두는 안은 버린다. 무엇이 진짜 원서인지 admin 과 학생이 매번 판단해야 하고, `application_{id}.pdf` 저장 키가 겹친다.

### 3.2 서식 1 전용 RPC 를 새로 둔다

`ApplicantResponse` 에 필드를 더하지 않고 `GetApplicationForm(applicant_id)` 를 새로 판다.

- `ApplicantResponse` 는 `ListApplicants` 가 **제출 원서 전체를 한 번에** 실어 나르는 메시지다. 교과성적 28칸·출결·보호자·주소를 얹으면 admin 의 지원자 목록 요청마다 회차 전원 × 그 값이 오간다
- 소비자가 다르다. 목록·수험표는 가벼운 요약, 서식 1 은 원서 전문이다
- `admin-applicant-via-grpc` 가 같은 message 의 9~14 번을 쓰는 중이라 **새 message 만 더하면 필드 번호가 겹치지 않는다**

### 3.3 데이터가 없는 칸은 비운다

| 칸 | 처리 | 이유 |
| --- | --- | --- |
| 학교코드 | **빈칸** | 원서에 저장되는 값이 없다. `institution_codes.code` 는 있지만 원서 작성 API 가 받지 않는다. 지금 채울 근거가 없다 |
| 보훈번호 | **빈칸** | 저장하는 곳이 없다. 어차피 옆 칸이 "교사 확인 + 서명"이라 종이에서 손으로 채우는 자리다 |
| 출신지역 | **빈칸** | 요강이 정의하지 않고 저장하는 값도 없다. `region` 은 아래 "지역" 칸(대전/전국)이라 여기 쓰면 뜻이 달라진다 |
| 수험번호 | **빈칸** | 서식이 `*기재하지 않음` 이라고 적어 뒀다 |
| 서명·날인·작성일자·추천서 | **빈칸** | 인쇄 후 수기 |

학교코드는 프론트가 학교 검색 응답으로 이미 code 를 받고 있어(`MiddleSchoolSearchResponse`) `middle_school_infos` 에 컬럼 하나와 요청 필드 하나만 더하면 채울 수 있다. 다만 기존 제출 원서는 비게 되고 이 작업의 범위 밖이다. 6절로 넘긴다.

### 3.4 특기사항은 특별전형 구분을 찍는다

`special_admission_type` 이 `NATIONAL_MERIT` 면 `국가유공자 자녀`, `SPECIAL_ADMISSION` 이면 `특례입학 대상자`, `NONE` 이면 빈칸.

요강이 이 칸을 정의하지 않아 추정이다. 다만 서식 1 에서 이 칸 옆이 지역·전형유형이고, 원서에 있는 값 중 "전형에 관한 덧붙임"은 이것뿐이다. 학교 담당자가 다른 뜻이라고 하면 빈칸으로 되돌린다.

### 3.5 교과성적 4열은 application 이 골라서 준다

document 가 학기를 고르면 `ScoreCalculator` 와 규칙이 두 벌이 되어 원서와 성적이 어긋난다. application 이 네 열을 채워 보낸다.

| 서식 열 | 채우는 학기 |
| --- | --- |
| 3학년 2학기 | `THIRD_GRADE_SECOND_SEMESTER` 성적. 졸업예정자는 없으므로 빈칸 |
| 3학년 1학기 | `THIRD_GRADE_FIRST_SEMESTER` 성적 |
| 직전학기 | `2-2 → 2-1 → 1-2 → 1-1` 순으로 성적이 있는 첫 학기 |
| 직전전학기 | 그 다음 학기 |

`ScoreCalculator` 의 후보 순서와 같다. 정상적인 졸업예정자·졸업자 모두 직전=2-2, 직전전=2-1 이 되고 3학년 2학기 열만 갈린다.

성취도 `X`(미이수·자유학기)는 **빈 문자열**로 보낸다. 요강에 없는 값이라 종이에 `X` 를 찍으면 등급으로 읽힌다.

한계 하나를 적어 둔다 — 졸업자의 3학년 2학기 성적이 없으면 `ScoreCalculator` 는 1학년까지 내려가 4개를 채우지만 서식에는 직전·직전전 두 칸뿐이라 나머지가 종이에 안 보인다. 서식의 칸이 모자라는 것이고 점수 산출과는 무관하다.

### 3.6 검정고시 지원자는 교과성적 표를 비운다

검정고시로 바꾸면 application 이 중학교 정보와 교과 성적을 지운다(`updateType`). 요강도 검정고시는 성적증명서를 따로 내고 입학전형위원회가 환산한다고만 정한다(8쪽).

따라서 출신학교·교과성적·원서작성자 교사 칸이 빈다. `ged_scores` 를 이 표에 찍지 않는다 — 열 이름이 학기라 검정고시 과목 점수를 넣을 자리가 아니다.

### 3.7 작성 중(DRAFT)인 원서도 만들어 준다

수험표와 같이 상태를 보지 않는다. 미제출이면 빈 칸이 많을 뿐이고, 제출 전에 인쇄해 확인하는 쓰임이 있다. 상태로 막으려면 그 규칙을 프론트와 맞춰야 하는데 지금 그럴 근거가 없다.

### 3.8 폰트는 지금 것을 그대로 쓴다

`NanumGothic-Regular.ttf` 하나로 찍는다. 제목·라벨의 굵기는 openhtmltopdf 의 합성 볼드로 낸다. 실측에서 읽는 데 지장이 없었고, Bold 를 더하면 리소스가 두 배가 된다. 인쇄물 품질이 문제되면 그때 `NanumGothic-Bold.ttf` 를 넣고 weight 700 으로 한 줄 더 등록한다.

### 3.9 원서는 본인 계정으로만 조회한다

경로에서 `applicantId` 를 빼고 게이트웨이가 넘긴 `X-User-Id` 로 자기 원서를 찾는다. `FileCategory.APPLICATION` 의 `downloaders` 도 `{OWNER}` 만 남는다.

- 원서 작성 API 가 경로에서 `applicantId` 를 뺐다. 프론트가 들고 다닐 값이 아니다
- 조회 키가 계정이라 남의 `applicantId` 를 훑을 수 없다. 그래서 "내 원서가 없다"를 403 으로 덮던 이유가 사라진다 — **404** 로 준다
- **관리자의 개별 원서 조회는 없어진다.** admin 이 한 명의 서식 1 을 받을 통로는 남기지 않는다. 원서 일괄 ZIP(6절)과 함께 정할 일이다
- 수험표(`/admission-tickets/{applicantId}`)는 그대로다. 관리자가 남의 수험표를 뽑는 쓰임이 남아 있다

## 4. gRPC 계약

`contracts/proto/application.proto` 에 **새 RPC 와 새 message 만 더한다.** 기존 message 는 건드리지 않는다.

```proto
service ApplicationService {
    // ...
    rpc GetApplicationForm(GetApplicationFormRequest) returns (ApplicationFormResponse);
}

// 원서 주인 계정(X-User-Id). 본인 원서만 찍으므로 applicant id 를 받지 않는다 (3.9).
message GetApplicationFormRequest {
    int64 account_id = 1;
}

// 요강 <서식 1> 입학원서 인쇄에 쓰는 원서 내용.
// ApplicantResponse 에 더하지 않는 것은 ListApplicants 가 제출 원서 전체를 한 번에 실어 나르기 때문이다.
message ApplicationFormResponse {
    int64 applicant_id = 1;
    int64 user_id = 2;
    ApplicantStatus applicant_status = 3;
    optional string name = 4;
    optional string phone_number = 5;
    optional string birthdate = 6;                  // yyyy-MM-dd
    Gender gender = 7;
    optional string address = 8;                    // 우편번호까지 합친 한 줄
    optional string photo_file_id = 9;
    Region region = 10;
    AdmissionType admission_type = 11;
    SpecialAdmissionType special_admission_type = 12;
    GraduationType graduation_type = 13;
    optional string graduation_date = 14;           // yyyy-MM
    optional string guardian_name = 15;
    optional string guardian_relation = 16;
    optional string guardian_phone_number = 17;
    optional MiddleSchool middle_school = 18;
    // 서식 1 교과성적(성취도) 표의 네 열. 성적이 없는 열은 담지 않는다.
    optional SemesterGrades third_grade_second_semester = 19;
    optional SemesterGrades third_grade_first_semester = 20;
    optional SemesterGrades previous_semester = 21;
    optional SemesterGrades second_previous_semester = 22;
    optional AcademicRecord academic_record = 23;
}

message MiddleSchool {
    string name = 1;
    string student_number = 2;
    string phone = 3;
    string teacher_name = 4;
}

// 성취도 A~E. 미이수(자유학기 등)는 빈 문자열이다.
message SemesterGrades {
    string korean = 1;
    string society = 2;
    string history = 3;
    string math = 4;
    string science = 5;
    string technology = 6;
    string english = 7;
}

// 원서에 성적을 한 번도 넣지 않았으면 이 message 가 없다. 0 과 구분된다.
message AcademicRecord {
    int32 absent_count = 1;
    int32 late_count = 2;
    int32 early_leave_count = 3;
    int32 class_absence_count = 4;
    int32 volunteer_time = 5;
    bool dsm_algorithm_awarded = 6;
    bool programming_certified = 7;
}

enum Gender {
    GENDER_UNSPECIFIED = 0;
    GENDER_MALE = 1;
    GENDER_FEMALE = 2;
}

enum SpecialAdmissionType {
    SPECIAL_ADMISSION_TYPE_UNSPECIFIED = 0;
    SPECIAL_ADMISSION_TYPE_NONE = 1;
    SPECIAL_ADMISSION_TYPE_NATIONAL_MERIT = 2;
    SPECIAL_ADMISSION_TYPE_SPECIAL_ADMISSION = 3;
}
```

원서가 없는 계정은 `NOT_FOUND`, 0 이하 id 는 `INVALID_ARGUMENT` — `GetApplicant` 와 같다.

## 5. REST API 변화

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| `PUT /api/document/v11/applications/{applicantId}` | 원서 파일 적재 (pdf·hwp, 10MB) | **삭제** |
| `GET /api/document/v11/applications/{applicantId}` | 올린 파일 조회. 없으면 `{ "exists": false }` | **`GET /api/document/v11/applications` 로 바뀐다.** 경로 인자가 없어지고 `X-User-Id` 로 본인 서식 1 PDF 를 새로 만들어 서명 URL 로 준다. 응답이 수험표와 같은 `FileResponse` 가 되고 `exists` 가 없어진다 |
| 파일명 | `application_{id}.pdf` 또는 `.hwp` | `application_{id}.pdf` 고정 |
| 권한 | 적재·조회 모두 관리자·본인 | 조회만 남고 **본인만** (`downloaders = {OWNER}`) |
| 원서가 없을 때 | `{ "exists": false }` | `404` |
| 게이트웨이 | — | 바뀌지 않는다. `/api/document` 접두사 그대로다 |

## 6. 하지 않는 것

- **학교코드·보훈번호·출신지역 수집** — 저장 위치부터 정해야 한다. 3.3 대로 지금은 비운다
- **submit 검증 강화** — 성적을 넣지 않고 제출할 수 있는 건 application 쪽 문제다. 이 작업은 빈 칸을 견디게만 한다
- **admin 의 원서 일괄 ZIP(`ExportType.APPLICATION`)** — admin 에 configuration 을 부르는 통로가 없다. 별개 작업이다
- **admin 수험표 양식 중복 제거** — `admin-applicant-via-grpc` 문서 6절과 같다
- **기존에 올라간 업로드 원서 정리** — 배포 전에 `application/` 접두사의 `files` 행 수를 확인한다. 0 이 아니면 옮길지 지울지 따로 정한다
- **webp 증명사진 지원** — openhtmltopdf(ImageIO)가 읽지 못해 빈 칸으로 찍힌다. 수험표와 같은 한계다

## 7. 영향과 배포

- application 을 먼저 배포하고 준비를 확인한 뒤 configuration 을 배포한다. 새 configuration 이 먼저 뜨면 `GetApplicationForm` 이 `UNIMPLEMENTED` 로 실패해 원서 조회가 전부 깨진다
- 자동 배포(`deploy.yml`)는 호스트마다 한꺼번에 띄워 순서를 보장하지 않는다. 호스트마다 `docker compose up -d application` 을 먼저 하고 준비를 확인한 뒤 나머지를 올린다
- 프론트(`EntryDSM/EntryDsm-Admission-2026` `apps/entry-admission`): 원서 업로드 화면을 없애고, 조회를 `GET /api/document/v11/applications` (경로 인자 없음)로 바꾸고 `exists` 분기를 지운다. **프론트를 먼저 바꾼다.** 없어진 `PUT`·`GET /applications/{id}` 는 매핑이 없어 `NoHandlerFoundException` 이 나는데, `DocumentExceptionHandler` 의 `Exception` 핸들러가 이걸 500 으로 내린다 — 09-17 과 같은 모양(404→500→게이트웨이 서킷 오픈)이다. 404 로 내리는 처리는 리뷰 대기 중인 PR #170 에 있다
- 롤백: 스키마 변경이 없어 코드만 되돌리면 된다. 생성된 `application_{id}.pdf` 객체는 남지만 이전 코드가 그대로 읽는다
- `admin-applicant-via-grpc` 와 같은 proto 파일을 건드린다. 그쪽을 먼저 병합하고 이 작업을 리베이스한다

## 8. 검증 계획

- `bazel test` application·configuration 통과
- 렌더 테스트: 서식 1 을 실제로 찍어 `%PDF-` 와 **A4 한 장**(`PDDocument.getNumberOfPages() == 1`)을 단언한다. 긴 주소·긴 학교명·긴 보호자명으로도 한 장인지 본다
- 로컬 E2E (MySQL 8.4·Redis·MinIO 컨테이너, `bazel-bin` 바이너리 직접 실행)
  - 원서를 끝까지 채우고 제출한 계정으로 `GET /api/document/v11/applications` → PDF 를 내려받아 눈으로 칸을 대조
  - 성적을 넣지 않은 제출 원서 → 교과·출결·봉사·가산점 칸이 비고 500 이 나지 않는지
  - 검정고시 지원자 → 출신학교·교과성적·교사 칸이 비는지
  - 졸업예정자 → 3학년 2학기 열이 비고 나머지 세 열이 차는지
  - 원서를 만들지 않은 계정 → 404, 관리자 토큰 → 403
  - `PUT /applications/{id}` → 매핑 없음. 지금은 404 가 아니라 **500** 이다 (위 7절)
  - application 을 끈 뒤 → 503 `APPLICATION_SERVICE_UNAVAILABLE`
