package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.admin.domain.enum.ApplicantStatus
import hs.kr.entrydsm.admin.domain.enum.ExportStatus
import hs.kr.entrydsm.admin.domain.enum.ExportType
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import hs.kr.entrydsm.admin.domain.model.ExportJob
import java.lang.reflect.Proxy
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.springframework.aop.framework.ProxyFactory
import org.springframework.aop.support.AopUtils

class ExportJobPersistenceAdapterTest {

    @Test
    fun `이전 산출물 잠금 조회는 활성 트랜잭션 안에서 실행한다`() {
        val repository = Proxy.newProxyInstance(javaClass.classLoader,
            arrayOf(ExportJobJpaRepository::class.java)) { _, method, _ ->
            assertEquals("findAllByTypeAndStatusAndObjectKeyIsNotNull", method.name)
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            emptyList<ExportJobJpaEntity>()
        } as ExportJobJpaRepository
        val manager = object : org.springframework.transaction.support.AbstractPlatformTransactionManager() {
            override fun doGetTransaction(): Any = Any()
            override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) = Unit
            override fun doCommit(status: org.springframework.transaction.support.DefaultTransactionStatus) = Unit
            override fun doRollback(status: org.springframework.transaction.support.DefaultTransactionStatus) = Unit
        }
        val proxy = ProxyFactory(ExportJobPersistenceAdapter(repository)).apply {
            isProxyTargetClass = true
            addAdvice(org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()))
        }.proxy as ExportJobPersistenceAdapter
        assertTrue(proxy.findDownloadableByType(ExportType.APPLICATION_CHECKLIST).isEmpty())
        assertTrue(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
    }

    @Test
    fun `트랜잭션용 클래스 프록시를 생성한다`() {
        val proxy = ProxyFactory(ExportJobPersistenceAdapter(echoingRepository())).apply {
            isProxyTargetClass = true
        }.proxy

        assertTrue(AopUtils.isCglibProxy(proxy))
    }

    @Test
    fun `대기 작업을 처리 중으로 선점한다`() {
        val now = Instant.parse("2026-09-29T00:00:00Z")
        val staleBefore = now.minusSeconds(1800)
        val pending = ExportJobJpaEntity(
            exportJobId = "exp_1",
            type = ExportType.FIRST_PASS,
            status = ExportStatus.PENDING,
            createdAt = Instant.EPOCH,
        )
        val repository = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExportJobJpaRepository::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findClaimable" -> {
                    assertEquals(listOf(staleBefore), args.toList())
                    pending
                }
                "save" -> args[0]
                else -> error("unexpected call: ${method.name}")
            }
        } as ExportJobJpaRepository

        val claimed = ExportJobPersistenceAdapter(repository).claimNext(now, staleBefore)

        assertEquals(ExportStatus.PROCESSING, claimed?.status)
        assertEquals(now, claimed?.startedAt)
    }

    /** 처리기는 저장 결과로 받은 작업의 필터로 지원자를 고른다. 비면 전체가 나간다. */
    @Test
    fun `저장 결과에 DB 에 남기지 않는 필터를 그대로 담는다`() {
        val filter = ApplicantFilter(statuses = setOf(ApplicantStatus.FIRST_PASS))

        val saved = ExportJobPersistenceAdapter(echoingRepository()).save(
            ExportJob(
                exportJobId = "exp_1",
                type = ExportType.FIRST_PASS,
                status = ExportStatus.PENDING,
                filter = filter,
                totalCount = 10,
                processedCount = 7,
                createdAt = Instant.EPOCH,
                failureCode = "APPLICATION_FORM_INVALID",
                failureMessage = "원서 데이터 오류",
                failedCount = 3,
            ),
        )

        assertEquals(filter, saved.filter)
        assertEquals(10, saved.totalCount)
        assertEquals(7, saved.processedCount)
        assertEquals("APPLICATION_FORM_INVALID", saved.failureCode)
        assertEquals("원서 데이터 오류", saved.failureMessage)
        assertEquals(3, saved.failedCount)
    }

    /** DB 없이 save 만 흉내 낸다. 받은 엔티티를 그대로 돌려준다. */
    private fun echoingRepository(): ExportJobJpaRepository =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExportJobJpaRepository::class.java),
        ) { _, method, args ->
            check(method.name == "save") { "unexpected call: ${method.name}" }
            args[0]
        } as ExportJobJpaRepository
}
