# Configuration Context

> 아래 기술한 내용은 Configuration 서비스 중 Document 도메인에 대한 내용입니다.

## Purpose
> PDF, Excel, 이미지 파일을 저장하고, 다운로드 URL을 제공하기 위한 서비스입니다.

## Responsibilities
* 원서 pdf/hwp 다운로드
* 수험표 pdf/hwp 다운로드
* 지원자 목록 excel 다운로드
* 원서 내 증명사진 첨부
* 공지사항 / QnA / 전형 요강 내 파일 첨부
  * `.pdf`, `.xlsx`, `.jpg`, `.png`, `.webp`, `.hwp`, `.docx` 지원

## Owns
* DB에 데이터를 저장하거나, 직접 데이터를 조회하지 않습니다.
* 스토리지 서비스(s3)에 파일을 저장하고, 제공합니다.

## Dependencies
* 해당 서비스는 타 context에 의존하지 않습니다.
* AWS S3 외부 서비스를 이용합니다.

## Out of Scope
> 필요할 경우 이 Context가 책임지지 않는 기능을 작성합니다
