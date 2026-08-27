package hs.kr.entrydsm.application.domain

import hs.kr.entrydsm.application.domain.service.ScoreCalculatorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationEnumTest::class,
    ScoreCalculatorTest::class,
)
class ApplicationDomainModuleTest
