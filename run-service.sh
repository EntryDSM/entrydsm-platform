#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SERVICE="${1:-}"

if [[ -z "${SERVICE}" ]]; then
  echo "사용법: $0 <service> [-- <추가 bazel 인자>]"
  echo "사용 가능한 서비스:"
  ls -d "${PROJECT_ROOT}"/systems/*/ 2>/dev/null | xargs -n1 basename || true
  exit 1
fi
shift

ENV_FILE="${PROJECT_ROOT}/systems/${SERVICE}/.env"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo ".env 파일이 존재하지 않습니다: ${ENV_FILE}"
  echo "사용 가능한 서비스:"
  ls -d "${PROJECT_ROOT}"/systems/*/ 2>/dev/null | xargs -n1 basename || true
  exit 1
fi

# 이 프로세스에만 env 로드 (서비스 간 격리)
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

TARGET="//systems/${SERVICE}/${SERVICE}-bootstrap:main"

echo "▶ ${SERVICE} 실행 (${ENV_FILE})"
exec bazel run "${TARGET}" "$@"