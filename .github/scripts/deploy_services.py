#!/usr/bin/env python3
"""변경된 빌드 입력으로 배포할 서비스 목록을 계산한다."""

import argparse
import json
from pathlib import Path
import re
import subprocess

SERVICES = ("gateway", "identity", "application", "notification", "configuration", "admin", "observability")
ROOT = Path(__file__).resolve().parents[2]


def select_services(paths, root=ROOT):
    # 문서와 CI 설정을 제외하고 실제 빌드 입력의 영향 범위를 모은다.
    selected = set()
    for name in paths:
        path = Path(name)
        parts = path.parts
        if not parts or path.suffix.lower() in {".md", ".rst"}:
            continue
        if parts[0] in {".github", ".codex", ".agents", "documents", "docs"} or "src/test" in path.as_posix():
            continue
        if len(parts) > 1 and parts[0] == "systems" and parts[1] in SERVICES:
            selected.add(parts[1])
        elif parts[0] in {"systems", "contracts", "packages", "tools", "scripts"} or name in {
            "Dockerfile", ".dockerignore", "prepare-images.sh", "BUILD.bazel", "MODULE.bazel",
            "MODULE.bazel.lock", ".bazelrc", ".bazelversion",
        } or path.suffix in {".bzl", ".bazel"} or path.name.startswith(".bazel"):
            return list(SERVICES)

    # 다른 서비스 모듈을 참조하는 빌드 대상도 함께 갱신한다.
    dependents = {service: set() for service in SERVICES}
    for consumer in SERVICES:
        directory = root / "systems" / consumer
        for build_file in directory.rglob("*"):
            if build_file.name != "BUILD.bazel" and build_file.suffix != ".bzl":
                continue
            for dependency in re.findall(r"//systems/([^/\s:\"]+)", build_file.read_text(encoding="utf-8")):
                if dependency in dependents:
                    dependents[dependency].add(consumer)
    pending = list(selected)
    while pending:
        for consumer in dependents[pending.pop()] - selected:
            selected.add(consumer)
            pending.append(consumer)
    return [service for service in SERVICES if service in selected]


def main():
    # push 전체 범위의 삭제·이동 전후 경로를 포함하고 수동 실행은 전체 배포한다.
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", default="")
    parser.add_argument("--after", default="HEAD")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    if args.all or not args.before or set(args.before) == {"0"}:
        services = list(SERVICES)
    elif subprocess.run(
        ["git", "cat-file", "-e", args.before + "^{commit}"], cwd=ROOT,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode:
        # 강제 push 등으로 이전 커밋이 없으면 누락 방지를 위해 전체 배포한다.
        services = list(SERVICES)
    else:
        paths = subprocess.check_output(
            ["git", "diff", "--name-only", "--no-renames", "-z", args.before, args.after], cwd=ROOT,
        ).decode("utf-8").split("\0")
        services = select_services(filter(None, paths))
    print("services=" + json.dumps(services, separators=(",", ":")))
    print("service_args=" + " ".join(services))
    print("has_services=" + str(bool(services)).lower())


if __name__ == "__main__":
    main()
