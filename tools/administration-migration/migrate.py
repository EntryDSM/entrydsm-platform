"""중단된 업무 테이블 이전. MySQL CLI만 사용하며 행 내용은 출력하지 않는다."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

TABLES = {
    "score_policy": "id policy_version subject_weight attendance_weight volunteer_weight rounding_scale effective_from updated_by".split(),
    "admission_quota": "id admission_type quota updated_at updated_by".split(),
    "screening": "applicant_id examinee_number is_arrived status arrived_at updated_at".split(),
    "export_job": "id export_job_id type status object_key total_count processed_count started_at failure_code failure_message failed_count created_at completed_at".split(),
    "schedule": "id title start_at end_at".split(),
}


def identifier(value):
    if not re.fullmatch(r"[A-Za-z0-9_]+", value):
        raise ValueError("INVALID_DATABASE_NAME")
    return f"`{value}`"


def literal(value):
    return "NULL" if value is None else "CONVERT(X'" + str(value).encode().hex() + "' USING utf8mb4)"


def digest(rows):
    return hashlib.sha256(json.dumps(rows, ensure_ascii=False, separators=(",", ":")).encode()).hexdigest()


class Database:
    def __init__(self, executable, config, schema):
        self.command = [executable, "--defaults-extra-file=" + str(Path(config).resolve()),
                        "--batch", "--raw", "--skip-column-names", "--default-character-set=utf8mb4", schema]
        self.schema = schema
        identifier(schema)
        if not Path(config).is_file():
            raise ValueError("MYSQL_CONFIG_NOT_FOUND")

    def execute(self, sql):
        result = subprocess.run(self.command, input="SET SESSION information_schema_stats_expiry=0;\n" + sql,
                                text=True, encoding="utf-8", capture_output=True)
        if result.returncode:
            # SQL 오류에는 실제 값이 포함될 수 있어 원문을 출력하지 않는다.
            match = re.search(r"ERROR (\d+)", result.stderr)
            raise RuntimeError("MYSQL_ERROR_" + (match[1] if match else "UNKNOWN"))
        return result.stdout.splitlines()

    def rows(self, table):
        expressions = [f"CAST({identifier(c)} AS CHAR)" if c != "is_arrived"
                       else "CAST(CAST(is_arrived AS UNSIGNED) AS CHAR)" for c in TABLES[table]]
        return [json.loads(line) for line in self.execute(
            f"SET time_zone='+00:00'; SELECT JSON_ARRAY({','.join(expressions)}) FROM {identifier(table)} ORDER BY {identifier(TABLES[table][0])};")]

    def sequence(self, table):
        values = self.execute("SELECT COALESCE(AUTO_INCREMENT,0) FROM information_schema.tables WHERE table_schema="
                              + literal(self.schema) + " AND table_name=" + literal(table) + ";")
        if len(values) != 1:
            raise ValueError("TABLE_NOT_FOUND")
        return int(values[0])


def verify_jobs(rows):
    if any(row[3] in ("PENDING", "PROCESSING") for row in rows):
        raise ValueError("EXPORT_JOBS_NOT_DRAINED")


def missing_rows(source, target):
    source_by_id = {row[0]: row for row in source}
    if len(source_by_id) != len(source):
        raise ValueError("DUPLICATE_SOURCE_ID")
    for row in target:
        if source_by_id.get(row[0]) != row:
            raise ValueError("TARGET_CONFLICT")
    target_ids = {row[0] for row in target}
    return [row for row in source if row[0] not in target_ids]


def guard(table, rows):
    columns = TABLES[table]
    conditions = [f"(SELECT COUNT(*) FROM {identifier(table)})={len(rows)}"]
    for row in rows:
        equal = " AND ".join(f"{identifier(c)} <=> " + (str(int(v)) if c == "is_arrived" else literal(v))
                             for c, v in zip(columns, row))
        conditions.append(f"EXISTS(SELECT 1 FROM {identifier(table)} WHERE {equal})")
    # 조건 불일치는 중복 키로 트랜잭션을 중단한다. CLI에 --force를 지정하지 않는다.
    return "INSERT INTO migration_guard SELECT 1 WHERE NOT (" + " AND ".join(conditions) + ");"


def transaction(snapshot, desired, replace=False):
    statements = ["SET time_zone='+00:00'; SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; START TRANSACTION;",
                  "CREATE TEMPORARY TABLE migration_guard(id INT PRIMARY KEY); INSERT INTO migration_guard VALUES(1);"]
    for table, rows in snapshot.items():
        statements += [f"SELECT {identifier(TABLES[table][0])} FROM {identifier(table)} FOR UPDATE;", guard(table, rows)]
    for table, rows in desired.items():
        insert = rows if replace else missing_rows(rows, snapshot[table])
        if replace:
            statements.append(f"DELETE FROM {identifier(table)};")
        for row in insert:
            values = [str(int(v)) if c == "is_arrived" else literal(v) for c, v in zip(TABLES[table], row)]
            statements.append(f"INSERT INTO {identifier(table)} ({','.join(map(identifier, TABLES[table]))}) VALUES ({','.join(values)});")
        statements.append(guard(table, rows))
    statements.append("COMMIT;")
    return "\n".join(statements)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql", default="mysql")
    for owner in ("admin", "configuration", "application"):
        parser.add_argument(f"--{owner}-config", required=True)
        parser.add_argument(f"--{owner}-database", required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--rollback-receipt", type=Path)
    parser.add_argument("--writers-stopped", action="store_true")
    parser.add_argument("--events-drained", action="store_true")
    parser.add_argument("--backup-file", type=Path)
    parser.add_argument("--receipt", type=Path, default=Path("migration-receipt.local.json"))
    args = parser.parse_args()
    db = {owner: Database(args.mysql, getattr(args, owner + "_config"), getattr(args, owner + "_database"))
          for owner in ("admin", "configuration", "application")}
    old = {table: db["configuration" if table == "schedule" else "admin"].rows(table) for table in TABLES}
    current = {table: db["application"].rows(table) for table in TABLES}
    verify_jobs(old["export_job"])
    verify_jobs(current["export_job"])
    old_hash = {table: digest(rows) for table, rows in old.items()}
    if args.rollback_receipt:
        receipt = json.loads(args.rollback_receipt.read_text(encoding="utf-8"))
        if receipt.get("applied") is not True or receipt.get("mode") != "forward" or receipt["legacy_hash"] != old_hash:
            raise ValueError("LEGACY_DATABASE_CHANGED")
        desired, target = current, old
    else:
        desired, target = old, current
        for table in TABLES:
            missing_rows(desired[table], target[table])
    sequences = {table: max(db["application"].sequence(table),
                            db["configuration" if table == "schedule" else "admin"].sequence(table)) for table in TABLES}
    report = {"mode": "rollback" if args.rollback_receipt else "forward", "applied": False,
              "legacy_hash": old_hash, "source_hash": {t: digest(r) for t, r in desired.items()},
              "counts": {t: len(r) for t, r in desired.items()}, "sequences": sequences}
    if args.apply:
        if not (args.writers_stopped and args.events_drained and args.backup_file and args.backup_file.is_file()):
            raise ValueError("FREEZE_DRAIN_AND_BACKUP_REQUIRED")
        report["backup_sha256"] = hashlib.file_digest(args.backup_file.open("rb"), "sha256").hexdigest()
        groups = {"configuration": ["schedule"], "admin": [t for t in TABLES if t != "schedule"]} if args.rollback_receipt else {"application": list(TABLES)}
        for owner, tables in groups.items():
            db[owner].execute(transaction({t: target[t] for t in tables}, {t: desired[t] for t in tables}, bool(args.rollback_receipt)))
            for table in tables:
                if sequences[table]:
                    db[owner].execute(f"ALTER TABLE {identifier(table)} AUTO_INCREMENT={sequences[table]};")
                if db[owner].rows(table) != desired[table]:
                    raise ValueError("POST_COPY_VERIFICATION_FAILED_KEEP_WRITERS_STOPPED")
        if args.rollback_receipt:
            # 구 이미지의 결과 이벤트도 현재 원서 버전보다 큰 번호로 재개해야 한다.
            version = int(db["application"].execute("SELECT COALESCE(MAX(screening_result_version),0)+1 FROM applicants;")[0])
            old_sequence = db["admin"].execute("SELECT COALESCE(AUTO_INCREMENT,1) FROM information_schema.tables WHERE table_schema="
                + literal(db["admin"].schema) + " AND table_name='screening_result_outbox';")
            if len(old_sequence) != 1:
                raise ValueError("ROLLBACK_OUTBOX_NOT_FOUND_KEEP_WRITERS_STOPPED")
            db["admin"].execute(f"ALTER TABLE screening_result_outbox AUTO_INCREMENT={max(version, int(old_sequence[0]))};")
        report["applied"] = True
        args.receipt.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, KeyError, OSError) as error:
        print(json.dumps({"success": False, "error": str(error) if isinstance(error, (ValueError, RuntimeError)) else type(error).__name__}))
        raise SystemExit(1)
