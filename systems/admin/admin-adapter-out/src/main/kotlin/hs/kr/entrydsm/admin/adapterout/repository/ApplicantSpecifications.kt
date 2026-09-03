package hs.kr.entrydsm.admin.adapterout.repository

import hs.kr.entrydsm.admin.adapterout.entity.ApplicantJpaEntity
import hs.kr.entrydsm.admin.domain.model.ApplicantFilter
import org.springframework.data.jpa.domain.Specification

/**
 * 지원자 목록 필터를 JPA Specification으로 옮깁니다.
 *
 * 비어 있는 조건은 아예 술어를 만들지 않아 전체 조회가 되게 합니다.
 */
private const val LIKE_ESCAPE = '\\'

object ApplicantSpecifications {

    fun of(filter: ApplicantFilter): Specification<ApplicantJpaEntity> =
        Specification { root, _, builder ->
            val predicates = buildList {
                filter.keyword?.takeIf { it.isNotBlank() }?.let { keyword ->
                    val pattern = "%${escapeLike(keyword.trim().lowercase())}%"
                    add(
                        builder.or(
                            builder.like(builder.lower(root.get("name")), pattern, LIKE_ESCAPE),
                            builder.like(
                                builder.lower(root.get("examineeNumber")),
                                pattern,
                                LIKE_ESCAPE,
                            ),
                        ),
                    )
                }
                filter.regions.takeIf { it.isNotEmpty() }
                    ?.let { add(root.get<Any>("region").`in`(it)) }
                filter.admissionTypes.takeIf { it.isNotEmpty() }
                    ?.let { add(root.get<Any>("admissionType").`in`(it)) }
                filter.graduationStatuses.takeIf { it.isNotEmpty() }
                    ?.let { add(root.get<Any>("graduationStatus").`in`(it)) }
                filter.statuses.takeIf { it.isNotEmpty() }
                    ?.let { add(root.get<Any>("status").`in`(it)) }
                filter.isSubmitted?.let {
                    add(builder.equal(root.get<Boolean>("isSubmitted"), it))
                }
            }

            if (predicates.isEmpty()) null else builder.and(*predicates.toTypedArray())
        }

    /** 검색어에 들어간 `%`, `_`는 와일드카드가 아니라 글자로 다뤄야 합니다. */
    private fun escapeLike(keyword: String): String =
        keyword.map { if (it == LIKE_ESCAPE || it == '%' || it == '_') "$LIKE_ESCAPE$it" else "$it" }
            .joinToString("")
}
