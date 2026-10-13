package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.admin.domain.command.UpdateApplicantStatusCommand
import hs.kr.entrydsm.admin.domain.enum.AdmissionType
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.exception.AdminDomainException
import hs.kr.entrydsm.admin.domain.model.Applicant
import hs.kr.entrydsm.admin.domain.port.out.*
import hs.kr.entrydsm.application.administration.application.ApplicantService
import hs.kr.entrydsm.application.administration.application.ScreeningService
import hs.kr.entrydsm.application.administration.domain.schedule.Schedule
import hs.kr.entrydsm.application.administration.domain.schedule.port.out.ScheduleRepository
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class FirstScreeningConcurrencyTest {
    @Test fun `MySQL 반복읽기에서 직접 선발과 강제 선발 경합은 127명에서 128명만 허용한다`() {
        val url = requireNotNull(System.getenv("ISSUE425_MYSQL_URL")) { "실제 MySQL URL이 필요합니다." }
        val suffix = java.util.UUID.randomUUID().toString().replace("-", "")
        val applicantTable = "issue425_applicants_$suffix"
        val scheduleTable = "issue425_schedule_$suffix"
        val connections = ThreadLocal<Connection>()
        fun connection() = requireNotNull(connections.get())
        fun open() = DriverManager.getConnection(url, "root", "")
        fun transaction(action: () -> String): String = open().use { conn ->
            conn.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            conn.autoCommit = false
            connections.set(conn)
            try {
                val result = action()
                conn.commit()
                result
            } catch (exception: Throwable) {
                conn.rollback()
                throw exception
            } finally { connections.remove() }
        }
        val directLocked = CountDownLatch(1)
        val releaseDirect = CountDownLatch(1)
        val forcedAttempt = CountDownLatch(1)
        val repository = proxy(ApplicantRepository::class.java) { name, args ->
            when (name) {
                "findAll", "findById" -> {
                    val sql = "SELECT id, status, score FROM $applicantTable" + if (name == "findById") " WHERE id = ?" else " ORDER BY id"
                    val applicants = connection().prepareStatement(sql).use { statement ->
                        if (name == "findById") statement.setLong(1, args[0] as Long)
                        statement.executeQuery().use { rows -> buildList {
                            while (rows.next()) add(Applicant(id = rows.getLong(1),
                                status = ApplicantStatus.valueOf(rows.getString(2)), totalScore = rows.getDouble(3),
                                admissionType = AdmissionType.GENERAL, submittedAt = Instant.EPOCH))
                        } }
                    }
                    if (name == "findById") applicants.singleOrNull() else applicants
                }
                "save" -> (args[0] as Applicant).also { applicant ->
                    connection().prepareStatement("UPDATE $applicantTable SET status = ? WHERE id = ?").use {
                        it.setString(1, applicant.status.name); it.setLong(2, applicant.id); assertEquals(1, it.executeUpdate())
                    }
                }
                else -> error("unexpected applicant call: $name")
            }
        }
        val schedules = proxy(ScheduleRepository::class.java) { name, _ ->
            assertEquals("findByTitleForUpdate", name)
            if (Thread.currentThread().name == "issue425-forced") forcedAttempt.countDown()
            connection().prepareStatement("SELECT id FROM $scheduleTable WHERE id = 1 FOR UPDATE").use { statement ->
                statement.executeQuery().use { assertTrue(it.next()) }
            }
            if (Thread.currentThread().name == "issue425-direct") {
                directLocked.countDown()
                assertTrue(releaseDirect.await(15, TimeUnit.SECONDS))
            }
            Schedule(title = "1차 합격 발표", startAt = LocalDateTime.of(2030, 1, 1, 0, 0), endAt = LocalDateTime.of(2030, 1, 1, 0, 0))
        }
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val screening = ScreeningService(repository, clock, schedules = schedules)
        val applicants = ApplicantService(repository, ApplicantArrivalPort { _, _ -> error("unexpected arrival") },
            distancePort = DistancePort { error("unexpected distance") }, clock = clock, screening = screening, schedules = schedules)
        val executor = Executors.newFixedThreadPool(2)
        var applicantsCreated = false
        var scheduleCreated = false
        try {
            open().use { conn -> conn.createStatement().use { statement ->
                assertTrue(conn.metaData.databaseProductVersion.startsWith("8.0."))
                statement.execute("CREATE TABLE $applicantTable (id BIGINT PRIMARY KEY, status VARCHAR(20) NOT NULL, score DOUBLE NOT NULL) ENGINE=InnoDB")
                applicantsCreated = true
                statement.execute("CREATE TABLE $scheduleTable (id BIGINT PRIMARY KEY) ENGINE=InnoDB")
                scheduleCreated = true
                statement.executeUpdate("INSERT INTO $scheduleTable VALUES (1)")
                for (id in 1..129) statement.executeUpdate("INSERT INTO $applicantTable VALUES ($id, '${if (id <= 127) "FIRST_PASS" else "PENDING"}', $id)")
            } }
            val direct = executor.submit<String> {
                Thread.currentThread().name = "issue425-direct"
                transaction { screening.selectFirst(128); "accepted" }
            }
            assertTrue(directLocked.await(15, TimeUnit.SECONDS))
            val forced = executor.submit<String> {
                Thread.currentThread().name = "issue425-forced"
                try {
                    transaction {
                        applicants.updateStatus(UpdateApplicantStatusCommand(129, ApplicantStatus.FIRST_PASS,
                            force = true, reason = "예비 합격 예정자 지정"))
                        "accepted"
                    }
                } catch (_: AdminDomainException) { "rejected" }
            }
            assertTrue(forcedAttempt.await(15, TimeUnit.SECONDS))
            releaseDirect.countDown()
            assertEquals("accepted", direct.get(15, TimeUnit.SECONDS))
            assertEquals("rejected", forced.get(15, TimeUnit.SECONDS))
            open().use { conn -> conn.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $applicantTable WHERE status = 'FIRST_PASS'").use {
                    assertTrue(it.next()); assertEquals(128, it.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM $applicantTable WHERE id <= 127 AND status = 'FIRST_PASS' AND score = id").use {
                    assertTrue(it.next()); assertEquals(127, it.getInt(1))
                }
                statement.executeQuery("SELECT status FROM $applicantTable WHERE id = 129").use {
                    assertTrue(it.next()); assertEquals("PENDING", it.getString(1))
                }
            } }
            transaction {
                applicants.updateStatus(UpdateApplicantStatusCommand(1, ApplicantStatus.FIRST_FAIL, force = true, reason = "개별 취소"))
                screening.selectFirst(1)
                "restored"
            }
        } finally {
            releaseDirect.countDown()
            executor.shutdownNow()
            executor.awaitTermination(15, TimeUnit.SECONDS)
            open().use { conn -> conn.createStatement().use { statement ->
                if (applicantsCreated) statement.execute("DROP TABLE $applicantTable")
                if (scheduleCreated) statement.execute("DROP TABLE $scheduleTable")
            } }
        }
    }

    private fun <T> proxy(type: Class<T>, invoke: (String, Array<out Any?>) -> Any?): T = type.cast(
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(type)) { _, method, args -> invoke(method.name, args ?: emptyArray()) })
}
