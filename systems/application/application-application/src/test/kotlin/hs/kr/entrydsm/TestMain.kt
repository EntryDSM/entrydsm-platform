package hs.kr.entrydsm.application.application

import hs.kr.entrydsm.application.application.service.ApplicationCommandServiceTest
import hs.kr.entrydsm.application.application.service.EvaluationCommandServiceTest
import hs.kr.entrydsm.application.application.service.PassResultCommandServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationCommandServiceTest::class,
    EvaluationCommandServiceTest::class,
    PassResultCommandServiceTest::class,
)
class ApplicationApplicationModuleTest
