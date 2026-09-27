package hs.kr.entrydsm.application.application

import hs.kr.entrydsm.application.application.service.ApplicationCommandServiceTest
import hs.kr.entrydsm.application.application.service.ApplicationPeriodGuardTest
import hs.kr.entrydsm.application.application.service.EvaluationCommandServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationCommandServiceTest::class,
    ApplicationPeriodGuardTest::class,
    EvaluationCommandServiceTest::class,
)
class ApplicationApplicationModuleTest
