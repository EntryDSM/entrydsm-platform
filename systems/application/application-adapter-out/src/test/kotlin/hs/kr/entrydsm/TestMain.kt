package hs.kr.entrydsm.application.adapterout

import hs.kr.entrydsm.application.adapterout.entity.ApplicantJpaEntityTest
import hs.kr.entrydsm.application.adapterout.entity.AcademicRecordJpaEntityTest
import hs.kr.entrydsm.application.adapterout.entity.ApplicantStatusOutboxJpaEntityTest
import hs.kr.entrydsm.application.adapterout.repository.LocalApplicationPeriodAdapterTest
import hs.kr.entrydsm.application.adapterout.grpc.GrpcAccountPhoneAdapterTest
import hs.kr.entrydsm.application.adapterout.repository.ApplicantPersistenceAdapterTest
import hs.kr.entrydsm.application.adapterout.repository.ApplicantSummaryQueryTest
import hs.kr.entrydsm.application.adapterout.repository.InstitutionCodeJpaRepositoryTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    hs.kr.entrydsm.application.adapterout.repository.ScreeningResultEventHandlerTest::class,
    AcademicRecordJpaEntityTest::class,
    ApplicantJpaEntityTest::class,
    hs.kr.entrydsm.application.adapterout.entity.PersonalDataConverterTest::class,
    ApplicantStatusOutboxJpaEntityTest::class,
    LocalApplicationPeriodAdapterTest::class,
    GrpcAccountPhoneAdapterTest::class,
    ApplicantPersistenceAdapterTest::class,
    ApplicantSummaryQueryTest::class,
    InstitutionCodeJpaRepositoryTest::class,
    hs.kr.entrydsm.application.adapterout.repository.ApplicantSnapshotRetentionTest::class,
)
class ApplicationAdapterOutModuleTest
