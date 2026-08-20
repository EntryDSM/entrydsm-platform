package hs.kr.entrydsm.identity.adapterin

import hs.kr.entrydsm.identity.adapterin.web.AuthControllerTest
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ResponseMapperTest
import hs.kr.entrydsm.identity.adapterin.web.exception.GlobalExceptionHandlerTest
import hs.kr.entrydsm.identity.adapterin.web.exception.RedisUnavailableExceptionHandlerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AuthControllerTest::class,
    GlobalExceptionHandlerTest::class,
    ResponseMapperTest::class,
    RedisUnavailableExceptionHandlerTest::class,
)
class AuthIdentityAdapterInModuleTest
