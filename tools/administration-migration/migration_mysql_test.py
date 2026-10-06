"""임시 로컬 MySQL에서 이전·재실행·충돌·역이전을 검증한다. 운영 DB는 사용하지 않는다."""
import hashlib
import json
import os
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
from pathlib import Path
import migrate


class MigrationMysqlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = shutil.which("mysql")
        cls.mysqld = shutil.which("mysqld")
        if not cls.mysql or not cls.mysqld:
            raise unittest.SkipTest("로컬 MySQL CLI와 서버가 필요하다")
        cls.temp = tempfile.TemporaryDirectory(prefix="entrydsm-migration-test-")
        cls.root = Path(cls.temp.name)
        cls.data = cls.root / "data"
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        hidden = {"creationflags": subprocess.CREATE_NO_WINDOW} if os.name == "nt" else {}
        init = subprocess.run([cls.mysqld, "--no-defaults", "--initialize-insecure", "--datadir=" + str(cls.data)],
                              capture_output=True, **hidden)
        if init.returncode:
            raise RuntimeError("ISOLATED_MYSQL_INIT_FAILED")
        cls.server = subprocess.Popen([cls.mysqld, "--no-defaults", "--datadir=" + str(cls.data),
            "--bind-address=127.0.0.1", f"--port={port}", "--mysqlx=OFF", "--log-error=" + str(cls.root / "server.log")],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **hidden)
        cls.config = cls.root / "client.cnf"
        cls.config.write_text(f"[client]\nhost=127.0.0.1\nport={port}\nuser=root\nprotocol=tcp\n", encoding="utf-8")
        command = [cls.mysql, "--defaults-extra-file=" + str(cls.config), "--batch", "--skip-column-names"]
        for _ in range(100):
            ready = subprocess.run(command, input="SELECT @@datadir;", text=True, capture_output=True, **hidden)
            if ready.returncode == 0:
                if Path(ready.stdout.strip()).resolve() != cls.data.resolve():
                    raise RuntimeError("ISOLATED_MYSQL_TARGET_MISMATCH")
                break
            time.sleep(0.1)
        else:
            raise RuntimeError("ISOLATED_MYSQL_NOT_READY")
        subprocess.run(command, input="CREATE DATABASE legacy_admin; CREATE DATABASE legacy_configuration; CREATE DATABASE new_application;",
                       text=True, check=True, capture_output=True, **hidden)
        cls.databases = {o: migrate.Database(cls.mysql, cls.config, schema) for o, schema in
            (("admin", "legacy_admin"), ("configuration", "legacy_configuration"), ("application", "new_application"))}
        repo = Path(__file__).resolve().parents[2]
        migrations = repo / "systems/application/application-bootstrap/src/main/resources/db/migration"
        admin_schema = (migrations / "V015__create_administration_tables.sql").read_text(encoding="utf-8")
        schedule_schema = (migrations / "V016__create_schedule.sql").read_text(encoding="utf-8")
        cls.databases["admin"].execute(admin_schema + "CREATE TABLE screening_result_outbox(id BIGINT AUTO_INCREMENT PRIMARY KEY);")
        cls.databases["configuration"].execute(schedule_schema)
        cls.databases["application"].execute(admin_schema + schedule_schema +
            "CREATE TABLE applicants(id BIGINT PRIMARY KEY, screening_result_version BIGINT); INSERT INTO applicants VALUES(5,1200);")
        # 연결/원본과 목적지의 collation이 달라도 문자열을 정확히 대조한다.
        for table in migrate.TABLES:
            cls.databases["application"].execute(
                f"ALTER TABLE {migrate.identifier(table)} CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")

    @classmethod
    def tearDownClass(cls):
        # Windows mysqld의 자식 프로세스도 정상 종료시켜 임시 데이터 파일 잠금을 해제한다.
        subprocess.run([cls.mysql, "--defaults-extra-file=" + str(cls.config), "-e", "SHUTDOWN;"], capture_output=True)
        cls.server.wait(timeout=20)
        for _ in range(100):
            try:
                cls.temp.cleanup()
                break
            except PermissionError:
                time.sleep(0.1)

    def test_forward_rerun_conflict_and_rollback(self):
        admin, config, app = (self.databases[o] for o in ("admin", "configuration", "application"))
        admin.execute("INSERT INTO score_policy VALUES(7,1,1.75,15,15,3,'2026-09-27 23:57:30.790217','test'); "
            "INSERT INTO admission_quota VALUES(9,'REGULAR',80,'2026-09-27 23:57:30.790217','test'); "
            "INSERT INTO screening VALUES(5,NULL,1,'FIRST_PASS','2026-09-27 23:57:30.790217',NULL); "
            "INSERT INTO export_job VALUES(11,'test-export','CHECKLIST','FAILED','stag/checklist/test.xlsx',85,84,NULL,'APPLICATION_SCORE_INVALID','',1,'2026-09-27 23:57:30.790217',NULL);")
        config.execute("INSERT INTO schedule VALUES(13," + migrate.literal("원서 접수") + ",'2026-09-01 00:00:00','2026-10-01 23:59:59');")
        receipt = self.root / "receipt.json"
        backup = self.root / "backup.sql"
        backup.write_text("테스트 전용 백업", encoding="utf-8")
        command = [shutil.which("python") or "python", str(Path(__file__).with_name("migrate.py")), "--mysql", self.mysql,
                   "--apply", "--writers-stopped", "--events-drained", "--backup-file", str(backup), "--receipt", str(receipt)]
        for owner, db in self.databases.items():
            command += [f"--{owner}-config", str(self.config), f"--{owner}-database", db.schema]
        def run(extra=(), success=True):
            result = subprocess.run(command + list(extra), text=True, encoding="utf-8", capture_output=True)
            self.assertEqual(0 if success else 1, result.returncode, result.stdout)
            return json.loads(result.stdout)
        self.assertTrue(run()["applied"])
        original_receipt = receipt.read_text(encoding="utf-8")
        self.assertTrue(run()["applied"])
        for table in migrate.TABLES:
            self.assertEqual((config if table == "schedule" else admin).rows(table), app.rows(table))
        # 조회 이후 발생한 충돌도 트랜잭션 안에서 중단한다.
        snapshot = {"screening": app.rows("screening")}
        app.execute("UPDATE screening SET status='first_pass' WHERE applicant_id=5;")
        with self.assertRaisesRegex(RuntimeError, "MYSQL_ERROR_1062"):
            app.execute(migrate.transaction(snapshot, snapshot))
        self.assertEqual("first_pass", app.rows("screening")[0][3])
        app.execute("UPDATE screening SET status='FINAL_PASS' WHERE applicant_id=5;")
        with self.assertRaisesRegex(RuntimeError, "MYSQL_ERROR_1062"):
            app.execute(migrate.transaction(snapshot, snapshot))
        self.assertEqual("FINAL_PASS", app.rows("screening")[0][3])
        run(success=False)
        receipt.write_text(original_receipt, encoding="utf-8")
        self.assertTrue(run(["--rollback-receipt", str(receipt)])["applied"])
        self.assertEqual(app.rows("screening"), admin.rows("screening"))
        self.assertGreaterEqual(int(admin.execute("SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema='legacy_admin' AND table_name='screening_result_outbox';")[0]), 1201)


if __name__ == "__main__":
    unittest.main()
