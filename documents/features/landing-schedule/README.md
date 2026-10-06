# 랜딩 일정 조회

`GET /api/application/v11/applicants/landing`은 요청마다 Application DB의 일정을 조회한다.

- 접수 시작·종료: 실제 접수 가능 여부와 동일한 `원서 접수` 일정의 `startAt`, `endAt`.
- 결과 발표: `1차 발표` 일정의 `startAt`. 저장소의 기존 일정 테스트에 사용하는 제목을 기준으로 하며, 운영 데이터 제목은 배포 전에 확인한다.
- 날짜는 기존과 동일한 서울 현지 시각의 `LocalDateTime` 형식을 유지한다.
- 일정 미등록 시 해당 날짜 필드는 `null`이다. `schedule.applicationPeriod` 객체는 유지한다.
- DB 조회 실패 시 `503`, `SCHEDULE_SERVICE_UNAVAILABLE`을 반환하며 미등록으로 처리하지 않는다.

`APPLICATION_START_AT`, `APPLICATION_END_AT`, `RESULT_ANNOUNCED_AT` 환경변수는 사용하지 않는다. 일정은 관리자 일정 API로 관리한다.

## 실행 검증

로컬 MySQL의 별도 검증 DB에서 Application 서버만 실행해 확인했다. 일정 환경변수 세 개를 제거한 상태에서 Flyway V001~V016 적용, `ddl-auto=validate` 검증, HTTP·gRPC 기동 및 health `UP`을 확인했다.

- 미등록: 접수 시작·종료 및 결과 발표가 `null`.
- DB 일정 등록·변경: 기존 날짜 형식으로 즉시 반영.
- DB 조회 실패: `503 SCHEDULE_SERVICE_UNAVAILABLE`, DB 복구 후 정상 응답.
- 관리자 일정 수정: 요청 시각과 DB 저장 시각, 랜딩 응답 시각이 일치.

JVM이 서울 시간대이고 JDBC가 UTC인 환경에서 `LocalDateTime`을 기존 Timestamp 방식으로 조회하면 일정이 9시간 이동했다. 일정 시작·종료에 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`을 지정해 직접 매핑하도록 수정하고 실제 MySQL 읽기·쓰기로 확인했다.

검증 환경의 Redis는 Streams 명령(`XGROUP`, `XINFO`)을 지원하지 않아 스냅샷·결과 이벤트 작업 오류가 남았다. 일정 조회는 정상 동작했으며 Redis 이벤트 흐름 전체는 이 환경에서 검증하지 못했다.

Docker의 Application·MySQL 8·Redis 7 구성에서도 health 및 readiness `UP`, 일정 미등록, 관리자 일정 등록·수정 후 랜딩 즉시 반영을 확인했다. Docker Redis에서는 Streams 명령이 동작했으며, 초기 `application.applicant-status` 스트림 부재로 스냅샷 정리 작업에 `ERR no such key` 로그가 발생했다. 이 기존 이벤트 작업 문제는 이번 일정 조회 변경 범위에 포함하지 않는다.
