package hs.kr.entrydsm.admin.adapterout

import hs.kr.entrydsm.admin.adapterout.grpc.GrpcNoticeAdapterTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AdminAdapterOutSmokeTest::class,
    GrpcNoticeAdapterTest::class,
)
class AdminAdapterOutModuleTest

class AdminAdapterOutSmokeTest {
    @Test
    fun moduleLoads() {
        assertTrue(true)
    }
}
