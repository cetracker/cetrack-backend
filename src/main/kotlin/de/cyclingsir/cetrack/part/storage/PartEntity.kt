package de.cyclingsir.cetrack.part.storage

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Initially created on 1/23/23.
 */
@Entity(name = "part")
@Table(name = "part")
@EntityListeners(AuditingEntityListener::class)
class PartEntity(
    @Id
    @GeneratedValue
    var id: UUID?,

    @Column(length = 255)
    var name: @NotNull String,

    @OneToMany(
        targetEntity = PartPartTypeRelationEntity::class,
        mappedBy = "part",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    var partTypeRelations: List<PartPartTypeRelationEntity>?,

    var boughtAt: Instant? = null,

    var retiredAt: Instant? = null,

    @CreatedDate
    var createdAt: Instant? = null
)



/*
 * Here we don’t use data classes with val properties because
 * JPA is not designed to work with immutable classes or the methods generated automatically by data classes.
 * If you are using other Spring Data flavor, most of them are designed to support such constructs
 * so you should use classes like data class User(val login: String, …)
 * when using Spring Data MongoDB, Spring Data JDBC, etc.
 */

/*
 * While Spring Data JPA makes it possible to use natural IDs,
 * it is not a good fit with Kotlin due to KT-6653,
 * https://youtrack.jetbrains.com/issue/KT-6653
 * that’s why it is recommended to always use entities with generated IDs in Kotlin.
 */
/*
    @ManyToMany(targetEntity = PartTypeEntity::class) @JoinTable(
        name = "part_part_types",
        joinColumns = [JoinColumn(name = "part_id", updatable = false, insertable = false)],
        inverseJoinColumns = [JoinColumn(name = "part_type_id", updatable = false, insertable = false)]
    )
    var partTypes: MutableList<PartTypeEntity> = mutableListOf(),
    ==>
    https://vladmihalcea.com/the-best-way-to-map-a-many-to-many-association-with-extra-columns-when-using-jpa-and-hibernate/
*/
