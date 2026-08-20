package hs.kr.entrydsm.identity.adapterout.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Component

/** Fails startup when refresh-token Redis is not durable and non-evicting in production. */
@Component
class RedisDurabilityGuard(
    private val connectionFactory: RedisConnectionFactory,
    @Value("\${identity.redis.durability-check-enabled:false}")
    private val enabled: Boolean,
) {
    init {
        if (enabled) verify()
    }

    private fun verify() {
        val connection = connectionFactory.connection
        try {
            val persistence = connection.serverCommands().info("persistence")
            val config = connection.serverCommands().getConfig("maxmemory-policy")
            check(persistence.getProperty("aof_enabled") == "1") {
                "Redis AOF persistence must be enabled for refresh-token state."
            }
            check(config.getProperty("maxmemory-policy") == "noeviction") {
                "Redis maxmemory-policy must be noeviction for refresh-token state."
            }
        } finally {
            connection.close()
        }
    }
}
