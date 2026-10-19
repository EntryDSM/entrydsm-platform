"""배포 대상 선정의 요구사항 기반 회귀 테스트."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("deploy_services.py")
SPEC = importlib.util.spec_from_file_location("deploy_services", SCRIPT)
deploy_services = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy_services)

ALL = ["gateway", "identity", "application", "notification", "configuration", "admin", "observability"]


class DeployServicesTest(unittest.TestCase):
    def select(self, *paths):
        return deploy_services.select_services(paths)

    def test_documentation_only_skips_deployment(self):
        self.assertEqual([], self.select("README.md", "docs/auth.rst", "systems/identity/README.md"))

    def test_unrelated_workflow_only_skips_deployment(self):
        self.assertEqual([], self.select(".github/workflows/lint.yml", ".github/ISSUE_TEMPLATE/bug.yml"))

    def test_no_changes_skips_deployment(self):
        self.assertEqual([], self.select())

    def test_identity_change_builds_only_identity(self):
        self.assertEqual(["identity"], self.select("systems/identity/identity-application/src/main/kotlin/AuthService.kt"))

    def test_multiple_services_are_selected_once(self):
        self.assertEqual(["identity", "notification"], self.select(
            "systems/notification/notification-domain/src/main/kotlin/Notification.kt",
            "systems/identity/identity-domain/src/main/kotlin/User.kt",
            "systems/identity/identity-domain/src/main/kotlin/Token.kt"))

    def test_documentation_does_not_hide_code_change(self):
        self.assertEqual(["identity"], self.select("README.md", "systems/identity/BUILD.bazel"))

    def test_common_code_affects_all_services(self):
        self.assertEqual(ALL, self.select("systems/common/src/main/kotlin/Shared.kt"))

    def test_root_build_inputs_affect_all_services(self):
        for path in ["Dockerfile", ".dockerignore", "MODULE.bazel", "MODULE.bazel.lock", ".bazelrc", ".bazelversion", "kotlin.bzl", "BUILD.bazel", "prepare-images.sh"]:
            with self.subTest(path=path):
                self.assertEqual(ALL, self.select(path))

    def test_shared_input_directories_affect_all_services(self):
        for path in ["contracts/example.proto", "packages/shared/BUILD.bazel", "tools/build.sh"]:
            with self.subTest(path=path):
                self.assertEqual(ALL, self.select(path))

    def test_deleted_service_file_still_selects_service(self):
        self.assertEqual(["identity"], self.select("systems/identity/deleted/Old.kt"))

    def test_unrelated_directory_does_not_match_service_prefix(self):
        self.assertNotEqual(["identity"], self.select("systems/identity-other/src/Main.kt"))

    def test_transitive_consumers_are_included(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for service, dependency in [("application", "configuration"), ("admin", "application")]:
                target = root / "systems" / service / "BUILD.bazel"
                target.parent.mkdir(parents=True)
                target.write_text(f'alias(name="main", actual="//systems/{dependency}/domain:main")', encoding="utf-8")
            self.assertEqual(["application", "configuration", "admin"], deploy_services.select_services(
                ["systems/configuration/domain/src/main/kotlin/Config.kt"], root=root))

    def test_initial_push_and_manual_deployment_select_all(self):
        for arguments in [[], ["--all"], ["--before", "0" * 40]]:
            with self.subTest(arguments=arguments):
                result = subprocess.run([sys.executable, str(SCRIPT), *arguments], capture_output=True, text=True, check=True)
                outputs = dict(line.split("=", 1) for line in result.stdout.splitlines())
                self.assertEqual(ALL, json.loads(outputs["services"]))
                self.assertEqual(" ".join(ALL), outputs["service_args"])
                self.assertEqual("true", outputs["has_services"])

    def test_unavailable_revision_deploys_all_instead_of_silently_skipping(self):
        result = subprocess.run([sys.executable, str(SCRIPT), "--before", "not-a-valid-deploy-revision"], capture_output=True, text=True)
        self.assertEqual(0, result.returncode)
        outputs = dict(line.split("=", 1) for line in result.stdout.splitlines())
        self.assertEqual(ALL, json.loads(outputs["services"]))
        self.assertEqual("true", outputs["has_services"])


if __name__ == "__main__":
    unittest.main()
