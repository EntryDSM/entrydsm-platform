package hs.kr.entrydsm.notification.application

import hs.kr.entrydsm.notification.application.service.NotificationServiceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    NotificationServiceTest::class,
)
class NotificationApplicationModuleTest
