package hs.kr.entrydsm.application.domain

import hs.kr.entrydsm.application.domain.service.ScoreCalculatorTest
import hs.kr.entrydsm.application.domain.service.DocumentPassCalculatorTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationEnumTest::class,
    ScoreCalculatorTest::class,
    DocumentPassCalculatorTest::class,
)
class ApplicationDomainModuleTest
