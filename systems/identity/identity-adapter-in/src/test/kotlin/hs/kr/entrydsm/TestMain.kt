package hs.kr.entrydsm.identity.adapterin

import hs.kr.entrydsm.identity.adapterin.web.dto.common.ResponseMapperTest
import hs.kr.entrydsm.identity.adapterin.web.exception.GlobalExceptionHandlerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    GlobalExceptionHandlerTest::class,
    ResponseMapperTest::class,
)
class IdentityAdapterInModuleTest
