package hs.kr.entrydsm.application.adapterout

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntityTest
import hs.kr.entrydsm.application.adapterout.entity.AcademicRecordJpaEntityTest
import hs.kr.entrydsm.application.adapterout.repository.ApplicantStatusOutboxEventTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AcademicRecordJpaEntityTest::class,
    ApplicantJpaEntityTest::class,
    ApplicantStatusOutboxEventTest::class,
)
class ApplicationAdapterOutModuleTest
