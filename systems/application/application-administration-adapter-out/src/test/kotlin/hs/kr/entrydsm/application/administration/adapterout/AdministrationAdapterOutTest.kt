package hs.kr.entrydsm.application.administration.adapterout

import hs.kr.entrydsm.application.administration.adapterout.distance.KakaoDistanceAdapterTest
import hs.kr.entrydsm.application.administration.adapterout.document.PoiXlsxAdapterTest
import hs.kr.entrydsm.application.administration.adapterout.persistence.ExportJobPersistenceAdapterTest
import hs.kr.entrydsm.application.administration.adapterout.persistence.LocalApplicantDataAdapterTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(KakaoDistanceAdapterTest::class, PoiXlsxAdapterTest::class,
    hs.kr.entrydsm.application.administration.adapterout.storage.GrpcFileMetadataAdapterTest::class,
    ExportJobPersistenceAdapterTest::class, LocalApplicantDataAdapterTest::class)
class AdministrationAdapterOutTest
