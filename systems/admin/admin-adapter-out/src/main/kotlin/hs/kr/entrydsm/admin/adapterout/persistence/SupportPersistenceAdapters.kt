package hs.kr.entrydsm.admin.adapterout.persistence

import hs.kr.entrydsm.admin.adapterout.entity.ExportJobJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.NoticeJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.QuestionAnswerJpaEntity
import hs.kr.entrydsm.admin.adapterout.entity.ScorePolicyJpaEntity
import hs.kr.entrydsm.admin.adapterout.repository.ExportJobJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.NoticeJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.QuestionAnswerJpaRepository
import hs.kr.entrydsm.admin.adapterout.repository.ScorePolicyJpaRepository
import hs.kr.entrydsm.admin.domain.model.ExportJob
import hs.kr.entrydsm.admin.domain.model.Notice
import hs.kr.entrydsm.admin.domain.model.QuestionAnswer
import hs.kr.entrydsm.admin.domain.model.ScorePolicy
import hs.kr.entrydsm.admin.domain.port.out.ExportJobRepository
import hs.kr.entrydsm.admin.domain.port.out.NoticeRepository
import hs.kr.entrydsm.admin.domain.port.out.QuestionAnswerRepository
import hs.kr.entrydsm.admin.domain.port.out.ScorePolicyRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Component

@Component
class ScorePolicyPersistenceAdapter(
    private val scorePolicyJpaRepository: ScorePolicyJpaRepository,
) : ScorePolicyRepository {

    override fun findCurrent(): ScorePolicy? =
        scorePolicyJpaRepository.findTopByOrderByPolicyVersionDesc()?.toDomain()

    override fun save(scorePolicy: ScorePolicy): ScorePolicy =
        scorePolicyJpaRepository.save(ScorePolicyJpaEntity.from(scorePolicy)).toDomain()
}

@Component
class ExportJobPersistenceAdapter(
    private val exportJobJpaRepository: ExportJobJpaRepository,
) : ExportJobRepository {

    override fun findByExportJobId(exportJobId: String): ExportJob? =
        exportJobJpaRepository.findByExportJobId(exportJobId)?.toDomain()

    override fun save(exportJob: ExportJob): ExportJob =
        exportJobJpaRepository.save(ExportJobJpaEntity.from(exportJob)).toDomain()
}

@Component
class NoticePersistenceAdapter(
    private val noticeJpaRepository: NoticeJpaRepository,
    private val clock: Clock,
) : NoticeRepository {

    override fun save(notice: Notice): Notice =
        noticeJpaRepository.save(NoticeJpaEntity.from(notice, Instant.now(clock))).toDomain()
}

@Component
class QuestionAnswerPersistenceAdapter(
    private val questionAnswerJpaRepository: QuestionAnswerJpaRepository,
    private val clock: Clock,
) : QuestionAnswerRepository {

    override fun save(questionAnswer: QuestionAnswer): QuestionAnswer =
        questionAnswerJpaRepository
            .save(QuestionAnswerJpaEntity.from(questionAnswer, Instant.now(clock)))
            .toDomain()
}
