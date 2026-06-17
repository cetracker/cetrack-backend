package de.cyclingsir.cetrack.part.storage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class ModifyPartPersistenceTest {

    @Autowired
    private lateinit var repository: PartRepository

    @Test
    fun `modifyPart issues UPDATE not INSERT and preserves createdAt`() {
        val initial = PartEntity(id = null, label = "Old Label", partTypeRelations = null)
        val saved = repository.saveAndFlush(initial)
        val id = saved.id!!
        val createdAt = saved.createdAt!!

        Assertions.assertEquals(1L, repository.count())

        val existing = repository.findById(id).get()
        existing.label = "New Label"
        repository.saveAndFlush(existing)

        Assertions.assertEquals(1L, repository.count())

        val updated = repository.findById(id).get()
        Assertions.assertEquals("New Label", updated.label)
        Assertions.assertEquals(createdAt, updated.createdAt)
    }
}
