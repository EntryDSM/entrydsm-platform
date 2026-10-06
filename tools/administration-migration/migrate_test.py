import unittest
import migrate


class MigrationTest(unittest.TestCase):
    def test_rerun_is_noop_and_conflicts_abort(self):
        rows = [["5", None, "1", "FIRST_PASS", "2026-09-29 06:05:10.090122", None]]
        self.assertEqual([], migrate.missing_rows(rows, rows))
        self.assertEqual(rows, migrate.missing_rows(rows, []))
        with self.assertRaisesRegex(ValueError, "TARGET_CONFLICT"):
            migrate.missing_rows(rows, [["5", None, "0", "FIRST_PASS", None, None]])
        with self.assertRaisesRegex(ValueError, "TARGET_CONFLICT"):
            migrate.missing_rows([], rows)

    def test_literals_preserve_unicode_null_and_sql_characters(self):
        value = "한글'\\\n;DROP TABLE screening;"
        literal = migrate.literal(value)
        self.assertEqual(value, bytes.fromhex(literal.split("'")[1]).decode())
        self.assertEqual("NULL", migrate.literal(None))
        self.assertEqual("CONVERT(X'' USING utf8mb4)", migrate.literal(""))
        with self.assertRaises(ValueError):
            migrate.identifier("database; DROP DATABASE application")

    def test_copy_and_rollback_guard_every_row_before_writing(self):
        row = ["5", None, "1", "FIRST_PASS", "2026-09-29 06:05:10.090122", None]
        sql = migrate.transaction({"screening": []}, {"screening": [row]})
        self.assertLess(sql.index("FOR UPDATE"), sql.index("INSERT INTO `screening`"))
        self.assertIn("(SELECT COUNT(*) FROM `screening`)=0", sql)
        self.assertIn("`is_arrived` <=> 1", sql)
        self.assertIn("CAST(`examinee_number` AS BINARY) <=> CAST(NULL AS BINARY)", sql)
        self.assertTrue(sql.endswith("COMMIT;"))
        rollback = migrate.transaction({"screening": [row]}, {"screening": []}, replace=True)
        self.assertLess(rollback.index("EXISTS(SELECT"), rollback.index("DELETE FROM `screening`"))
        self.assertIn("(SELECT COUNT(*) FROM `screening`)=0", rollback)

    def test_active_jobs_block_cutover(self):
        for status in ("PENDING", "PROCESSING"):
            with self.assertRaisesRegex(ValueError, "NOT_DRAINED"):
                migrate.verify_jobs([["1", "export_1", "CHECKLIST", status]])
        migrate.verify_jobs([["1", "export_1", "CHECKLIST", "FAILED"]])


if __name__ == "__main__":
    unittest.main()
