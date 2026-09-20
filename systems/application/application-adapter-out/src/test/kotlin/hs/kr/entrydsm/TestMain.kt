package hs.kr.entrydsm.application.adapterout

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntityTest
import hs.kr.entrydsm.application.adapterout.entity.AcademicRecordJpaEntityTest
import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntityTest
import hs.kr.entrydsm.application.adapterout.repository.InstitutionCodeJpaRepositoryTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AcademicRecordJpaEntityTest::class,
    ApplicantJpaEntityTest::class,
    ApplicantStatusOutboxJpaEntityTest::class,
    InstitutionCodeJpaRepositoryTest::class,
)
class ApplicationAdapterOutModuleTest
