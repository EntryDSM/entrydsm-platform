package hs.kr.entrydsm.application.administration.application

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(ScreeningServiceTest::class, FirstScreeningSchedulerTest::class, FinalScreeningSchedulerTest::class, ApplicationCorrectionServiceTest::class, ApplicantServiceTest::class)
class FirstScreeningModuleTest
