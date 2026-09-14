FROM debian:bookworm-slim

ARG SERVICE
WORKDIR /app

COPY dist/${SERVICE}/main /app/main
COPY dist/${SERVICE}/main.runfiles /app/main.runfiles
COPY dist/${SERVICE}/main.runfiles_manifest /app/main.runfiles_manifest

RUN chmod +x /app/main

EXPOSE 8080

ENTRYPOINT ["/app/main"]
