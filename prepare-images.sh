#!/usr/bin/env bash
set -euo pipefail

targets=()
for service in "$@"; do
  targets+=("//:${service}")
done

bazel --batch build "${targets[@]}"

for service in "$@"; do
  source="bazel-bin/systems/${service}/${service}-bootstrap"
  destination="dist/${service}"
  mkdir -p "${destination}"
  cp "${source}/main" "${destination}/main"
  cp -LR "${source}/main.runfiles" "${destination}/main.runfiles"
  cp "${source}/main.runfiles_manifest" "${destination}/main.runfiles_manifest"
done
