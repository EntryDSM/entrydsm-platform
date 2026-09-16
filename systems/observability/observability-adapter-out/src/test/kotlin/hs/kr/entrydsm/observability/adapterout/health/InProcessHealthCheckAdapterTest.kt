package hs.kr.entrydsm.observability.adapterout.health

import hs.kr.entrydsm.observability.domain.enum.ServiceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InProcessHealthCheckAdapterTest {
    @Test
    fun reportsProcessDependenciesWhenHealthy() {
        val adapter = InProcessHealthCheckAdapter {
            ProcessHealth(up = true, dependencies = mapOf("db" to true, "redis" to false))
        }

        val health = adapter.check(ServiceName.IDENTITY)

        assertNotNull(health.responseTimeMs)
        assertEquals(mapOf("db" to true, "redis" to false), health.dependencies)
    }

    /** 상태가 UP 이 아니면 응답 시간을 비워 두어 대시보드가 DOWN 으로 판정하게 한다. */
    @Test
    fun leavesResponseTimeEmptyWhenTheProcessIsNotUp() {
        val adapter = InProcessHealthCheckAdapter { ProcessHealth(up = false, dependencies = emptyMap()) }

        val health = adapter.check(ServiceName.APPLICATION)

        assertNull(health.responseTimeMs)
        assertEquals(emptyMap<String, Boolean>(), health.dependencies)
    }

    /** 한 프로세스이므로 어떤 논리 서비스로 물어도 같은 상태를 준다. */
    @Test
    fun answersTheSameForEveryLogicalService() {
        val adapter = InProcessHealthCheckAdapter { ProcessHealth(up = true, dependencies = mapOf("db" to true)) }

        val dependencies = ServiceName.entries.map { adapter.check(it).dependencies }

        assertEquals(1, dependencies.distinct().size)
    }
}
