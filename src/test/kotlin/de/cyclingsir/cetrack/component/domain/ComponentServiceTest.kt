package de.cyclingsir.cetrack.component.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.storage.ComponentDomain2StorageMapper
import de.cyclingsir.cetrack.component.storage.ComponentRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID

/**
 * Pure validation branches - the persistence-facing behavior lives in
 * ComponentCrudIT (PG Testcontainers).
 */
class ComponentServiceTest {

    private val repository = mockk<ComponentRepository>()
    private val service = ComponentService(repository, ComponentDomain2StorageMapper())

    private fun aComponent(label: String = "front tire") =
        DomainComponent(componentTypeId = UUID.randomUUID(), label = label)

    @Test
    fun `blank label is rejected`() {
        val ex = assertThrows<ServiceException> { service.addComponent(aComponent(label = "   ")) }
        assertEquals(ErrorCodesDomain.COMPONENT_DATA_INVALID, ex.getError())
    }

    @Test
    fun `price and currency must come as a pair - both directions`() {
        val priceOnly = assertThrows<ServiceException> {
            service.addComponent(aComponent().copy(price = "10.50"))
        }
        assertEquals(ErrorCodesDomain.COMPONENT_PRICE_CURRENCY_MISMATCH, priceOnly.getError())

        val currencyOnly = assertThrows<ServiceException> {
            service.addComponent(aComponent().copy(priceCurrency = "EUR"))
        }
        assertEquals(ErrorCodesDomain.COMPONENT_PRICE_CURRENCY_MISMATCH, currencyOnly.getError())
    }

    @Test
    fun `constraint violation on save maps to COMPONENT_DATA_INVALID`() {
        every { repository.saveAndFlush(any()) } throws DataIntegrityViolationException("fk violation")

        val ex = assertThrows<ServiceException> { service.addComponent(aComponent()) }
        assertEquals(ErrorCodesDomain.COMPONENT_DATA_INVALID, ex.getError())
    }

    @Test
    fun `status precedence is retired over mounted over inAssembly over inStock`() {
        assertEquals(DomainComponentStatus.RETIRED, service.statusOf(retired = true, mounted = true, member = true))
        assertEquals(DomainComponentStatus.MOUNTED, service.statusOf(retired = false, mounted = true, member = true))
        assertEquals(DomainComponentStatus.IN_ASSEMBLY, service.statusOf(retired = false, mounted = false, member = true))
        assertEquals(DomainComponentStatus.IN_STOCK, service.statusOf(retired = false, mounted = false, member = false))
    }
}
