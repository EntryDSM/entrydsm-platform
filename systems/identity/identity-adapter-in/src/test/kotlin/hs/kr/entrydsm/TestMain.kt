package hs.kr.entrydsm.identity.adapterin

import hs.kr.entrydsm.identity.adapterin.web.AccountControllerTest
import hs.kr.entrydsm.identity.adapterin.web.dto.common.ResponseMapperTest
import hs.kr.entrydsm.identity.adapterin.web.ApplicationControllerTest
import hs.kr.entrydsm.identity.adapterin.web.AuthControllerTest
import hs.kr.entrydsm.identity.adapterin.web.PassControllerTest
import hs.kr.entrydsm.identity.adapterin.web.exception.GlobalExceptionHandlerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AuthControllerTest::class,
    PassControllerTest::class,
    AccountControllerTest::class,
    ResponseMapperTest::class,
    ApplicationControllerTest::class,
    GlobalExceptionHandlerTest::class,
)
class IdentityAdapterInModuleTest
