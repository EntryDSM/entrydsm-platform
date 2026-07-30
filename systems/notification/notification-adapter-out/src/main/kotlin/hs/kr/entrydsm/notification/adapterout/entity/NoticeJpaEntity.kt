package hs.kr.entrydsm.notification.adapterout.entity

import hs.kr.entrydsm.notification.domain.model.Notice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "notices")
open class NoticeJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "title", nullable = false, length = 255)
    var title: String = "",

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    var content: String = "",

    @Column(name = "author", nullable = false, length = 50)
    var author: String = "",

    @Column(name = "view_count", nullable = false)
    var viewCount: Int = 0,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toDomain(): Notice =
        Notice(
            id = requireNotNull(id),
            title = title,
            content = content,
            author = author,
            viewCount = viewCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

