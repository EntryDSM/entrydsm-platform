package hs.kr.entrydsm.identity.adapterout.security

import java.util.Properties
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisServerCommands

class RedisDurabilityGuardTest {
    @Test
    fun enabledGuardRejectsRedisWithoutAof() {
        val connection = mock(RedisConnection::class.java)
        val factory = mock(RedisConnectionFactory::class.java)
        val serverCommands = mock(RedisServerCommands::class.java)
        val persistence = Properties().apply { setProperty("aof_enabled", "0") }
        val config = Properties().apply { setProperty("maxmemory-policy", "noeviction") }

        `when`(factory.connection).thenReturn(connection)
        `when`(connection.serverCommands()).thenReturn(serverCommands)
        `when`(serverCommands.info("persistence")).thenReturn(persistence)
        `when`(serverCommands.getConfig("maxmemory-policy")).thenReturn(config)

        assertThrows(IllegalStateException::class.java) {
            RedisDurabilityGuard(factory, true)
        }
    }

    @Test
    fun enabledGuardAcceptsAofAndNoeviction() {
        val connection = mock(RedisConnection::class.java)
        val factory = mock(RedisConnectionFactory::class.java)
        val serverCommands = mock(RedisServerCommands::class.java)
        val persistence = Properties().apply { setProperty("aof_enabled", "1") }
        val config = Properties().apply { setProperty("maxmemory-policy", "noeviction") }

        `when`(factory.connection).thenReturn(connection)
        `when`(connection.serverCommands()).thenReturn(serverCommands)
        `when`(serverCommands.info("persistence")).thenReturn(persistence)
        `when`(serverCommands.getConfig("maxmemory-policy")).thenReturn(config)

        RedisDurabilityGuard(factory, true)
    }
}
