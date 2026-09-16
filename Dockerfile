FROM debian:bookworm-slim

ARG SERVICE
WORKDIR /app

# 저장된 시각을 UTC 로 읽고 쓰는 코드가 있어 컨테이너 시간대를 고정한다.
ENV TZ=UTC

COPY dist/${SERVICE}/main /app/main
COPY dist/${SERVICE}/main.runfiles /app/main.runfiles
COPY dist/${SERVICE}/main.runfiles_manifest /app/main.runfiles_manifest

RUN chmod +x /app/main

EXPOSE 8080

ENTRYPOINT ["/app/main"]
