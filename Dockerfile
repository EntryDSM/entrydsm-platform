FROM gcr.io/bazel-public/bazel:8.5.1 AS builder

ARG SERVICE
WORKDIR /workspace

COPY . .
RUN bazel build //:${SERVICE}

FROM eclipse-temurin:17-jre

ARG SERVICE
WORKDIR /app

COPY --from=builder /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main /app/main
COPY --from=builder /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main.runfiles /app/main.runfiles
COPY --from=builder /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main.runfiles_manifest /app/main.runfiles_manifest

RUN chmod +x /app/main

EXPOSE 8080
ENTRYPOINT ["/app/main"]
