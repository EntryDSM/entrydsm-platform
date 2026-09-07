FROM gcr.io/bazel-public/bazel:8.5.1 AS builder

ARG SERVICE
WORKDIR /workspace

COPY . .

RUN bazel --batch build //:${SERVICE}

RUN mkdir -p /workspace/out && \
    cp /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main /workspace/out/main && \
    cp -LR /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main.runfiles /workspace/out/main.runfiles && \
    cp /workspace/bazel-bin/systems/${SERVICE}/${SERVICE}-bootstrap/main.runfiles_manifest /workspace/out/main.runfiles_manifest

FROM debian:bookworm-slim

WORKDIR /app

COPY --from=builder /workspace/out/main /app/main
COPY --from=builder /workspace/out/main.runfiles /app/main.runfiles
COPY --from=builder /workspace/out/main.runfiles_manifest /app/main.runfiles_manifest

RUN chmod +x /app/main

EXPOSE 8080

ENTRYPOINT ["/app/main"]