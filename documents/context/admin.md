# Admin Context

## Purpose
> 원서, 일정, 최종 합격, 공지 등록 등의 관리자 관련 업무를 처리합니다.

## Responsibilities
* 수험번호 자동 부여
* 합격자 산출
  * 서류 합격
  * 최종 합격
  * 단체 메시지 전송
* 원서 조회
  * 지원자 목록 조회
    * 엑셀로 내보내기
  * 지원자 상세 조회
  * 수험표 다운로드
  * 원서 조회 및 다운로드
* 공지 등록
* QnA 등록
* 통계 조회(analytics)
* 성적 산출 방식 변경
* notification.schedule 데이터 조회
* 전형 일정 단계
* 원서 제출 기간
* 전형 마감일 / 일정에 따라 변화
* application 데이터 조회
    * 신입생 지원률
    * 경쟁률
    * 전형별 접수 현황
    * 지원자 성비
    * 지역별 접수 현황

> [!NOTE]
> 아직 미확정된 부분이 일부 존재합니다. 참고해주세요.

## Owns

*no body*

## Dependencies
* application
* notification
* configuration

## Out of Scope
> 필요할 경우 이 Context가 책임지지 않는 기능을 작성합니다
