# 관리자 업무 소유권 이전과 성적 복구

새 Admin은 DB 없는 BFF이고 Application이 일정·정책·전형·내보내기와 문서 생성의 소유자다. Configuration은 파일과 메타데이터, 기존 환경변수 CRUD만 처리한다. 기존 지원자·성적·개인정보 테이블과 PK는 이동하지 않는다. 이 디렉터리의 명령은 운영 DB에 자동으로 실행되지 않는다.

## 전환 순서

1. 구 이미지의 정확한 태그·환경변수·라우팅을 보관한다. 점검 시간을 확보하고 게이트웨이에서 학생·관리자 쓰기를 중단한다. 구 Admin worker가 `export_job`의 PENDING/PROCESSING을 해소하도록 기다린 후 worker를 정지한다.
2. Application 상태 outbox, Admin 결과 outbox, Redis 소비 그룹의 pending 이벤트를 모두 해소한다. 구 이미지의 이벤트 생산·소비와 정기 작업을 정지한다. 새 코드와 구 worker를 동시에 쓰기 소유자로 실행하지 않는다.
3. 세 DB 전체를 백업하고 복원 가능 여부를 확인한다. `applicant_export_projection`, `applicant_export_event`, `screening_result_outbox`도 백업한다. 기존 파일 버킷과 키는 그대로 유지한다.
4. Application의 V015/V016을 적용한다. 관리자·문서 업무는 `APPLICATION_ADMINISTRATION_ENABLED=false` 상태로 준비한다. 새 Application은 접수 일정을 로컬에서 읽으므로 **일정 이전 전 학생 트래픽을 재개하면 안 된다**. 새 Configuration/Admin/Gateway 이미지도 최종 전환 전에 기존 서비스 대신 배포하지 않는다.
5. 아래 기본 검증 명령으로 충돌을 확인한다. 누락 ID만 추가하고, 같은 ID의 다른 값·대상에만 있는 행은 중단한다. 이전 대상은 `score_policy`, `admission_quota`, `screening`, `export_job`, `schedule`이다. 원서 사본은 이전 대상이 아니다.
6. 백업·정지·이벤트 해소를 확인한 후 `--apply --writers-stopped --events-drained --backup-file ... --receipt cutover.local.json`을 추가한다. 적용 후 ID·모든 열·행 수·해시·AUTO_INCREMENT를 대조한다. 실패하면 계속 정지 상태를 유지한다.
7. 새 Configuration에 기존 문서와 내보내기 버킷을 허용하고 접근 권한을 부여한다. Application에 기존 버킷·STORAGE_ENV·만료 시간·지도 설정을 옮긴다. Configuration의 AWS 자격 증명으로 두 버킷에 접근할 수 있어야 한다. Admin에는 DB/S3/스냅샷 키가 필요 없다.
8. Application의 관리자 기능을 활성화하고 새 Admin·Configuration·Gateway와 함께 전환한다. 기존 HTTP URL은 유지한다. 관리자 상세·전체 목록·기간 경계·권한·통계·내보내기·기존 다운로드 링크를 점검한다. 없는 원서는 404, 성적/원서 오류는 502이며 빈 결과나 성공 파일로 바꾸지 않는다. 이후 쓰기를 재개한다.

일정과 관리자 쓰기는 기존 Gateway 인증·권한 검증을 사용한다. 새 Admin은 Application만 호출하고 공지·문의에는 Notification을 호출한다. 문서 생성 경로(`/api/document/v11/applications`, `/admission-tickets/{id}`, `/registration-documents/latest`)는 Application으로, 일반 업로드·파일 조회·삭제는 Configuration으로 간다. OpenAPI는 업무를 소유한 서비스의 스펙에 포함된다.

## 데이터 이전 명령

Python 3.11 이상과 MySQL 8 CLI가 필요하다. DB 이름은 영문·숫자·밑줄만 허용한다. 암호는 명령 인자에 쓰지 않고 권한을 제한한 각 서비스의 MySQL option 파일에 저장한다. 아래 경로는 운영자가 준비하는 예시다. option 파일·백업·receipt·복구 증빙에는 민감 정보가 있으므로 Git에 추가하지 않는다.

```bash
python3 tools/administration-migration/migrate.py \
  --admin-config /etc/entrydsm/secrets/admin.cnf --admin-database admin_db \
  --configuration-config /etc/entrydsm/secrets/configuration.cnf --configuration-database configuration_db \
  --application-config /etc/entrydsm/secrets/application.cnf --application-database application_db
```

기본값은 검증 모드이며 데이터·SQL·암호를 출력하지 않는다. 적용 결과 receipt에도 행 수·해시·시퀀스와 백업 해시만 남는다. 이전은 Application의 한 트랜잭션에서 대상 행을 잠그고 조회 당시 내용과 일치하는지 확인한 뒤 적용한다. 시퀀스 변경은 MySQL DDL이므로 데이터 커밋 뒤 수행한다. 마지막 대조가 성공하기 전에는 쓰기를 재개하지 않는다. 재실행은 이미 동일한 행을 변경하지 않는다.

## 복귀

쓰기 재개 전 실패하면 구 이미지·환경변수·라우팅을 복원하고 새 worker를 정지한 후 재개한다. 구 DB는 삭제하지 않는다.

쓰기 재개 후에는 다시 모든 쓰기·worker·이벤트를 정지하고 현재 세 DB를 새로 백업한다. 이전 때 보관한 **forward receipt**를 `--rollback-receipt cutover.local.json`으로 지정한다. 검증 후 적용 옵션과 별도 `--receipt rollback.local.json`을 추가한다. 새 Application 업무 행을 구 Admin/Configuration으로 역이전하여 신규·변경·삭제와 ID·시각·파일 키를 반영한다. 구 DB가 최초 receipt 이후 바뀌었으면 덮어쓰지 않고 중단한다. 결과 outbox의 다음 번호도 현재 원서 screening_result_version보다 크게 올린다.

두 구 DB를 하나의 트랜잭션으로 커밋할 수 없으므로 역이전 중 한쪽만 성공한 경우 **계속 정지 상태를 유지**한다. 새 백업으로 구 DB를 복원하거나 완료된 내용을 대조한 후 재시도한다. 마지막 대조와 결과 이벤트 번호 확인 후에만 구 이미지·worker·라우팅을 재개한다. 복귀 후 오래된 원서 프로젝션을 새 관리자 조회의 근거로 다시 사용하지 않도록 별도로 점검한다.

## 근거 있는 성적 복구

운영 applicantId=5의 실제 점수는 현재 정보로 복구하지 않았다. 총점으로 점수를 역산하거나 임의 점수를 입력하지 않는다. 백업/binlog를 별도 DB에 복원하여 원래 성적을 찾고, 없으면 담당자가 공식 제출 증빙을 확인한다. 둘 다 없으면 중단한다.

오프라인 실행 대상: `//systems/application/application-bootstrap:score_recovery`. 일반 서버와 달리 HTTP/gRPC 서버·worker·Redis relay를 등록하지 않는다. Application DB와 개인정보/스냅샷 키가 있는 제한된 운영 환경에서만 실행한다. 증빙 JSON(64KiB 이하)에는 다음 **모든 필드**를 기록한다.

- `applicantId`, `accountId`, `academicRecordId`, `expectedStatusVersion`: 현재 원서와 성적 부모 행의 ID 및 버전.
- `graduationType`, `admissionType`: 현재 원서와 일치해야 한다.
- `source`: `ISOLATED_BACKUP` 또는 `OFFICIAL_DOCUMENT`.
- `sourceReference`, `verifiedBy`: 별도 복원 DB/공식 기록 식별자와 검증 담당자.
- `artifactPath`, `artifactSha256`: 검증한 실제 증빙 파일과 SHA-256.
- `academicRecord`: 증빙으로 확인한 성적·출결·봉사·가산점 전체. `absentCount`, `lateCount`, `earlyLeaveCount`, `classAbsenceCount`, `volunteerTime`, `isDsmAlgorithmAwarded`, `isProgrammingCertified`, `subjectGrades`, `gedScores`를 생략하지 않는다. 해당 없음은 담당자가 확인한 빈 맵/null로 기록한다. GED 과목별 7개 점수 또는 학기별 실제 등급을 모두 입력한다.

```bash
bazel run //systems/application/application-bootstrap:score_recovery -- --dry-run /etc/entrydsm/secrets/recovery-proof.json
# 쓰기/이벤트 정지와 백업 확인 후 운영자가 적용한다.
RECOVERY_WRITERS_STOPPED=true \
  bazel run //systems/application/application-bootstrap:score_recovery -- --apply /etc/entrydsm/secrets/recovery-proof.json
```

검증 모드는 증빙 해시·대상 버전을 검증하고 점수를 계산하되 저장하지 않는다. 적용 모드는 원서를 잠그고 성적 부모 PK·접수 상태·제출 시각·수험번호·전형 결과를 유지한 채 성적·총점·산출 시각·상태 버전과 outbox를 한 트랜잭션에서 저장한다. 성적 산출·원서 변환·outbox 실패 시 전부 롤백한다. 같은 증빙을 재적용하면 변경된 버전 때문에 중단한다. 출력에는 성공/실패·대상 ID·버전만 포함하며 성적·개인정보를 출력하지 않는다.

## 로컬 검증

```bash
cd tools/administration-migration
python3 -m unittest migrate_test migration_mysql_test -v
```

MySQL 통합 검증은 임시 데이터 디렉터리·임의의 localhost 포트·테스트 전용 DB를 만들고 정상 종료한다. 운영 option 파일은 사용하지 않는다. 이전·재실행·시각/NULL/문자 보존·충돌 중단·역이전·이벤트 시퀀스 연속성을 확인한다. 성적 복구의 증빙 실패 무변경과 outbox 실패 롤백은 `score_recovery_test`의 H2 트랜잭션 테스트로 검증한다.
