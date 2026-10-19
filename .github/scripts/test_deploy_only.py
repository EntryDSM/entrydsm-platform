"""수동 배포 워크플로의 실제 입력 처리 및 원격 명령 검증."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).parents[1] / "workflows" / "deploy-only.yml"
ALL = "gateway identity application notification configuration admin observability"


def step_script(name):
    section = WORKFLOW.read_text(encoding="utf-8").split(f"- name: {name}\n", 1)[1]
    lines = section.split("run: |\n", 1)[1].splitlines()
    body = []
    for line in lines:
        if line.strip() and not line.startswith("          "):
            break
        body.append(line)
    return textwrap.dedent("\n".join(body))


class DeployOnlyTest(unittest.TestCase):
    def prepare(self, **changes):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            env = dict(os.environ, BRANCH="develop", SERVICE="identity", INPUT_IMAGE_TAG="", STAG_HOSTS='["stag.example.com"]', PROD_HOSTS='["prod.example.com"]', GITHUB_OUTPUT=str(output))
            env.update(changes)
            result = subprocess.run([sys.executable, "-c", step_script("서비스와 이미지 태그 검증")], env=env, capture_output=True, text=True)
            values = dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines()) if output.exists() else {}
            return result, values

    def test_branch_defaults_and_environment_hosts(self):
        for branch, stage in [("develop", "stag"), ("main", "prod")]:
            with self.subTest(branch=branch):
                result, output = self.prepare(BRANCH=branch)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(stage, output["stage"])
                self.assertEqual(stage, output["image_tag"])
                self.assertEqual([f"{stage}.example.com"], json.loads(output["hosts"]))
                self.assertEqual("identity", output["services"])

    def test_all_and_explicit_tag(self):
        result, output = self.prepare(SERVICE="all", INPUT_IMAGE_TAG="release-2026.10_19")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(ALL, output["services"])
        self.assertEqual("release-2026.10_19", output["image_tag"])

    def test_each_supported_service(self):
        for service in ALL.split():
            with self.subTest(service=service):
                result, output = self.prepare(SERVICE=service)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(service, output["services"])

    def test_invalid_inputs_rejected_before_outputs(self):
        cases = [{"BRANCH": "feature/test"}, {"SERVICE": "identity notification"}, {"SERVICE": "identity;id"}]
        cases += [{"INPUT_IMAGE_TAG": tag} for tag in ["bad tag", "x;id", "$(id)", "-bad", "a" * 129, "tag\nother"]]
        cases += [{"STAG_HOSTS": hosts} for hosts in ['[]', '{}', '["host;id"]', '[42]']]
        for changes in cases:
            with self.subTest(changes=changes):
                result, output = self.prepare(**changes)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual({}, output)

    def test_tag_length_boundary(self):
        result, output = self.prepare(INPUT_IMAGE_TAG="a" * 128)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("a" * 128, output["image_tag"])

    @unittest.skipUnless(shutil.which("bash"), "Bash 실행 환경 필요")
    def test_remote_commands_only_deploy_selected_service(self):
        script = step_script("기존 이미지 배포")
        mocks = '''
ssh() { bash -c "${@: -1}"; }
docker() { printf 'DOCKER'; printf ' <%s>' "$@"; printf '\\n'; }
export -f docker
'''
        for stage in ["stag", "prod"]:
            with self.subTest(stage=stage):
                env = dict(os.environ, GHCR_USERNAME="test", GHCR_TOKEN="fake", DEPLOY_USER="tester", HOST="mock", DEPLOY_PATH=".", IMAGE_TAG="existing-sha", SERVICES="identity", STAGE=stage)
                result = subprocess.run([shutil.which("bash"), "-s"], input=mocks + script, env=env, capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertRegex(result.stdout, r"DOCKER <compose>[^\n]* <pull> <identity>")
                self.assertRegex(result.stdout, r"DOCKER <compose>[^\n]* <up> <-d> <--no-deps> <identity>")
                self.assertNotIn("<build>", result.stdout)
                self.assertNotIn("<push>", result.stdout)
                self.assertNotIn("<gateway>", result.stdout)
                self.assertEqual(stage == "stag", "DOCKER <compose> <restart> <nginx>" in result.stdout)

    @unittest.skipUnless(shutil.which("bash") and shutil.which("docker"), "Bash와 Docker Compose 필요")
    def test_real_compose_preserves_dollar_environment_and_relative_mount(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "compose.yaml").write_text('name: deploy-fixture\nservices:\n  identity:\n    image: original:latest\n    environment:\n      SECRET: "literal$$TOKEN"\n    volumes:\n      - ./data:/data\n', encoding="utf-8")
            baseline = subprocess.run([shutil.which("docker"), "compose", "-f", str(root / "compose.yaml"), "config", "--format", "json"], capture_output=True, text=True, check=True)
            original_environment = json.loads(baseline.stdout)["services"]["identity"]["environment"]
            mocks = '''
ssh() { bash -c "${@: -1}"; }
docker() {
  if [ "$1" = login ]; then return 0; fi
  local args=("$@")
  if [[ " ${args[*]} " == *" pull "* ]]; then
    "$DOCKER_EXE" "${args[@]:0:${#args[@]}-2}" config --format json
  elif [[ " ${args[*]} " == *" up "* ]]; then
    return 0
  else
    "$DOCKER_EXE" "$@"
  fi
}
export -f docker
'''
            env = dict(os.environ, GHCR_USERNAME="test", GHCR_TOKEN="fake", DEPLOY_USER="tester", HOST="mock", DEPLOY_PATH=root.as_posix(), IMAGE_TAG="existing-sha", SERVICES="identity", STAGE="prod", DOCKER_EXE=Path(shutil.which("docker")).as_posix())
            result = subprocess.run([shutil.which("bash"), "-s"], input=mocks + step_script("기존 이미지 배포"), env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            config = json.loads(result.stdout)
            self.assertEqual("deploy-fixture", config["name"])
            identity = config["services"]["identity"]
            self.assertEqual("ghcr.io/entrydsm/entrydsm-identity:existing-sha", identity["image"])
            self.assertEqual(original_environment, identity["environment"])
            self.assertEqual((root / "data").resolve(), Path(identity["volumes"][0]["source"]).resolve())


if __name__ == "__main__":
    unittest.main()
