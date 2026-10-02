# 내보내기 및 원서 조회 오류

`POST /api/v11/admin/exports`는 작업 접수 결과를 반환한다. 이후 상태 조회의 `success=true`는 조회 요청 성공을 뜻하며, 파일 생성 성공 여부는 `data.status`로 판단한다.

실패 작업에는 다음 필드가 추가된다.

| 필드 | 의미 |
| --- | --- |
| `failureCode` | 개인정보 없는 고정 오류 코드. 성공·미완료 및 변경 전 실패 작업은 null |
| `failureMessage` | 코드에 대응하는 안전한 한국어 설명. 원격 예외 메시지를 그대로 노출하지 않음 |
| `failedCount` | 원서별 검증·누락으로 확인된 실패 대상 수. 전체 통신 장애처럼 건별 결과를 얻지 못하면 0 |
| `totalCount` | 대상 수를 확인한 시점의 전체 대상 수. 대상 조회 자체가 실패하면 0 |
| `processedCount` | 기존 산출물 생성 단계의 처리 건수. 프로젝션 동기화 건수와 다름 |

내부 gRPC 오류는 사용자 요청 본문 오류인 `INVALID_REQUEST_BODY`(400)와 구분한다.

| 오류 코드 | 의미 |
| --- | --- |
| `APPLICATION_INVALID_ID` | 내부 원서 조회에 전달된 ID 오류 |
| `APPLICATION_SCORE_INVALID` | 저장된 원서의 성적 상세 계산 실패. 성적·전형 데이터 확인 필요 |
| `APPLICATION_FORM_INVALID` | 원서 검증·변환 또는 로컬 원서 데이터 오류 |
| `APPLICATION_SNAPSHOT_INVALID` | 로컬 원서 암호화 데이터·스냅샷 키 문제 |
| `APPLICATION_FORM_NOT_FOUND` | 동기화할 원서 누락 또는 대상 계정 불일치 |
| `APPLICATION_SYNC_FAILED` | 여러 종류의 원서 실패 또는 알 수 없는 배치 실패 코드 |
| `ADMISSION_TICKET_INVALID_DATA` | configuration 수험표 렌더링 요청 데이터 거부 |
| `UPSTREAM_INVALID_ARGUMENT` | 다른 내부 연동 요청 거부 |

위 오류는 HTTP 502로 분류한다. 공개 API 요청 자체의 검증 실패는 기존 400을 유지한다. 기존 서비스 장애·대상 없음 오류도 기존 분류를 유지한다.

application의 배치 원서 조회는 정상 원서와 계정별 `failures`를 함께 반환한다. admin은 정상 원서를 계속 동기화하되, 실패·누락·계정 불일치가 있으면 기존 원서 삭제 대사와 파일 생성을 중단한다. 일부 원서가 빠진 체크리스트를 정상 완료로 제공하지 않는다. 공통 저장소·설정·통신 장애는 배치 전체 실패로 처리한다.

수험표는 admin → configuration → application 경로를 거친다. configuration은 알려진 원서 오류 코드를 유지하고, admin은 렌더링 RPC와 원서 오류를 구분해 기록한다. 원서 조회는 application gRPC 또는 admin의 로컬 원서 조회 모델 경로에 따라 발생 위치가 다르므로 같은 원인으로 단정하지 않는다.

로그에서 작업 ID, RPC, gRPC 상태, 고정 오류 코드와 대상 ID를 확인한다. 임의 예외 메시지 대신 예외 타입·발생 위치를 기록하며 원서 본문·주소·API 키를 남기지 않는다. 운영 장애의 정확한 원인은 해당 시각의 서비스 로그로 확인한다.

배포 시 application·configuration·admin을 함께 갱신하고 admin Flyway `V011__add_export_failure_details.sql` 적용을 확인한다. proto 필드는 추가 방식이라 직렬화 호환성을 유지하지만, 구버전 소비자는 실패 목록을 확인하지 않으므로 부분 결과 격리를 이용하려면 생산자와 소비자를 함께 갱신해야 한다.

## 수험표 시간 초과와 완료 후 정리 오류 진단

`ADMISSION_TICKET_GENERATION_FAILED`와 `grpcStatus=DEADLINE_EXCEEDED`가 함께 있으면 원서 데이터 오류가 아니라 제한 시간 내 응답을 받지 못한 것이다. admin의 기본 `CONFIGURATION_GRPC_DEADLINE_MS`는 10000이며 요청 인원수를 곱해 적용한다. `failedCount=0`으로도 작업 전체가 실패할 수 있다.

admin·configuration 갱신 후 수험표를 다시 요청하고 같은 시각의 로그를 비교한다.

1. configuration에 `Document gRPC started`가 없으면 admin의 `CONFIGURATION_GRPC_HOST`·`CONFIGURATION_GRPC_PORT`, configuration gRPC 서버 등록·연결 상태를 확인한다.
2. 진입 로그가 있으면 `Admission ticket stage`의 시작·완료와 `elapsedMs`를 확인한다. 단계는 `applicant_lookup`, `photo_lookup`, `photo_download`, `photo_resize`, `sheet_render`이며 사진 없는 지원자는 사진 단계를 건너뛴다.
3. 시작만 있는 단계는 아직 처리 중이거나 중단된 구간이다. 지원자 조회는 application, 사진 조회는 DB, 다운로드는 S3, 이미지·엑셀 생성은 configuration에서 확인한다. 서버 종료 여부도 함께 확인한다.
4. 단계별 실측으로 병목을 해결한 뒤 필요한 경우 제한 시간을 조정하고 완료·파일 다운로드까지 재검증한다.

체크리스트 완료 후 `Previous export lookup failed`가 있으면 이전 산출물 정리가 실패한 것이다. 새 파일 완료 상태는 유지한다. 이전 산출물 조회의 비관적 잠금은 짧은 트랜잭션 안에서 실행하며, S3 삭제는 그 트랜잭션 밖에서 처리한다. 정리 실패 시 이전 파일이 남을 수 있으므로 원인을 수정한 뒤 다음 정상 내보내기에서 재정리됐는지 확인한다.
