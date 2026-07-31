package hs.kr.entrydsm.identity.domain

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    AccountTest::class,
    StudentProfileTest::class,
)
class IdentityDomainModuleTest
