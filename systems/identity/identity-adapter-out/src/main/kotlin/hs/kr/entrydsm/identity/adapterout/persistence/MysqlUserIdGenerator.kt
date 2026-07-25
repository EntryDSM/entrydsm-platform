package hs.kr.entrydsm.identity.adapterout.persistence

import hs.kr.entrydsm.identity.application.port.out.UserIdGenerator
import java.sql.Connection
import javax.sql.DataSource

/**
 * Allocates user IDs from a durable MySQL counter row.
 *
 * The counter row must be created by the database schema:
 * `identity_user_id_sequence(sequence_name VARCHAR(32) PRIMARY KEY, next_id BIGINT NOT NULL)`.
 */
class MysqlUserIdGenerator(
    private val dataSource: DataSource,
) : UserIdGenerator {
    override fun nextId(): Long {
        dataSource.connection.use { connection ->
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            return try {
                incrementCounter(connection)
                val nextId = readLastInsertedId(connection)
                check(nextId > 0) { "생성된 사용자 ID가 올바르지 않습니다." }
                connection.commit()
                nextId
            } catch (exception: Throwable) {
                connection.rollback()
                throw exception
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }
    }

    private fun incrementCounter(connection: Connection) {
        connection.prepareStatement(INCREMENT_COUNTER_SQL).use { statement ->
            statement.setString(1, SEQUENCE_NAME)
            check(statement.executeUpdate() == 1) {
                "사용자 ID 시퀀스 행이 존재하지 않습니다."
            }
        }
    }

    private fun readLastInsertedId(connection: Connection): Long =
        connection.prepareStatement(READ_LAST_INSERTED_ID_SQL).use { statement ->
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "사용자 ID 시퀀스 값을 읽지 못했습니다." }
                resultSet.getLong(1)
            }
        }

    companion object {
        private const val SEQUENCE_NAME = "USER"
        private const val INCREMENT_COUNTER_SQL =
            "UPDATE identity_user_id_sequence " +
                "SET next_id = LAST_INSERT_ID(next_id + 1) " +
                "WHERE sequence_name = ?"
        private const val READ_LAST_INSERTED_ID_SQL = "SELECT LAST_INSERT_ID()"
    }
}
