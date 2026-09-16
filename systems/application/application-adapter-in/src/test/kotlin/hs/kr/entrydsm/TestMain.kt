package hs.kr.entrydsm.application.adapterin

import hs.kr.entrydsm.application.adapterin.api.ApplicationApiAdapterTest
import hs.kr.entrydsm.application.adapterin.web.ApplicationControllerTest
import hs.kr.entrydsm.application.adapterin.web.EvaluationControllerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationApiAdapterTest::class,
    ApplicationControllerTest::class,
    EvaluationControllerTest::class,
)
class ApplicationAdapterInModuleTest
