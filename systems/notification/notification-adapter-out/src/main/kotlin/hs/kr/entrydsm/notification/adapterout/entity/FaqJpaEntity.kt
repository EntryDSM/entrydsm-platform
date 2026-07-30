package hs.kr.entrydsm.notification.adapterout.entity

import hs.kr.entrydsm.notification.domain.model.Faq
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "faqs")
open class FaqJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "category", nullable = false, length = 50)
    var category: String = "",

    @Column(name = "question", nullable = false, length = 255)
    var question: String = "",

    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    var answer: String = "",

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): Faq =
        Faq(
            id = requireNotNull(id),
            category = category,
            question = question,
            answer = answer,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

