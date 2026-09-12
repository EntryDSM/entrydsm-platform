-- 공지사항의 소유자는 notification 이다. admin 은 gRPC 로 등록만 위임하므로
-- admin DB 의 공지 테이블은 더 이상 쓰이지 않는다.
-- 이미 쌓인 행은 테스트 데이터로 판단해 이관하지 않는다. (#139)
DROP TABLE IF EXISTS notice;
