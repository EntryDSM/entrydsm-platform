# Analytics Context

## Purpose
> 각 서비스에서 통계 데이터를 가져옵니다. admission 서비스에 해당 API를 제공합니다.

* admission에서 통계 데이터를 만들지 않고 analytics 서비스의 API를 조회하는 이유는 admission 서비스가 너무 비대해지는 현상을 방지하기 위함압니다.

## Responsibilities
* schedule 데이터 조회
  * 전형 일정 단계 
  * 원서 제출 기간
  * 전형 마감일 / 일정에 따라 변화 
* application 데이터 조회
  * 신입생 지원률
  * 경쟁률
  * 전형별 접수 현황
  * 지원자 성비
  * 지역별 접수 현황

## Owns

todo

## Dependencies
* schedule
* application

## Out of Scope
- 데이터를 직접 저장하지 않고, 필요한 데이터를 담당 서비스에서 조회하여 가져옵니다.