package hs.kr.entrydsm.identity.adapterin

import hs.kr.entrydsm.identity.adapterin.web.AccountControllerTest
import hs.kr.entrydsm.identity.adapterin.web.AuthControllerTest
import hs.kr.entrydsm.identity.adapterin.web.exception.GlobalExceptionHandlerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AuthControllerTest::class,
    AccountControllerTest::class,
    GlobalExceptionHandlerTest::class,
)
class IdentityAdapterInModuleTest
