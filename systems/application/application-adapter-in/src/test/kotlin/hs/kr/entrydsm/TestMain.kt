package hs.kr.entrydsm.application.adapterin

import hs.kr.entrydsm.application.adapterin.grpc.ApplicationGrpcServiceTest
import hs.kr.entrydsm.application.adapterin.web.ApplicationControllerTest
import hs.kr.entrydsm.application.adapterin.web.EvaluationControllerTest
import hs.kr.entrydsm.application.adapterin.web.MiddleSchoolControllerTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    ApplicationGrpcServiceTest::class,
    ApplicationControllerTest::class,
    EvaluationControllerTest::class,
    MiddleSchoolControllerTest::class,
)
class ApplicationAdapterInModuleTest
