# syntax=docker/dockerfile:experimental
FROM gcr.io/bazel-public/bazel:8.5.1 AS builder
WORKDIR /workspace

COPY . .