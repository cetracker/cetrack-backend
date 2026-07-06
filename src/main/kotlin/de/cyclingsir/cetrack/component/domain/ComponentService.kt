package de.cyclingsir.cetrack.component.domain

import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.component.storage.ComponentDomain2StorageMapper
import de.cyclingsir.cetrack.component.storage.ComponentEntity
import de.cyclingsir.cetrack.component.storage.ComponentRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ComponentService(
    private val repository: ComponentRepository,
    private val mapper: ComponentDomain2StorageMapper,
) {

    @Transactional(readOnly = true)
    fun getComponents(componentTypeId: UUID?, status: DomainComponentStatus?): List<DomainComponent> {
        val entities = componentTypeId
            ?.let { repository.findAllByComponentTypeId(it) }
            ?: repository.findAll()
        val mounted = repository.activelyMountedComponentIds().toSet()
        val members = repository.activeMemberComponentIds().toSet()
        return entities
            .map { entity -> mapper.map(entity).copy(status = deriveStatus(entity, mounted, members)) }
            .filter { status == null || it.status == status }
    }

    @Transactional(readOnly = true)
    fun getComponent(componentId: UUID): DomainComponent {
        val entity = repository.findById(componentId)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND) }
        return mapper.map(entity).copy(status = deriveStatus(entity))
    }

    @Transactional
    fun addComponent(component: DomainComponent): DomainComponent {
        requireIdentifiable(component)
        requirePricePair(component)
        val entity = try {
            repository.saveAndFlush(mapper.map(component))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_DATA_INVALID,
                "Component references an unknown component type or violates a constraint.", e)
        }
        return mapper.map(entity).copy(status = DomainComponentStatus.IN_STOCK)
    }

    @Transactional
    fun modifyComponent(componentId: UUID, component: DomainComponent): DomainComponent {
        val existing = repository.findById(componentId)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND) }
        requireIdentifiable(component)
        requirePricePair(component)
        if (existing.componentTypeId != component.componentTypeId
            && (repository.hasActiveMounting(componentId) || repository.hasActiveMembership(componentId))
        ) {
            // otherwise the mount-time type-match invariant (domain-model.md §2) would silently break
            throw ServiceException(ErrorCodesDomain.COMPONENT_IN_USE,
                "Type can't change while the component is mounted or an assembly member; dismount/remove first.")
        }
        existing.componentTypeId = component.componentTypeId
        existing.label = component.label
        existing.manufacturer = component.manufacturer
        existing.model = component.model
        existing.serialNumber = component.serialNumber
        existing.vendor = component.vendor
        existing.purchaseDate = component.purchaseDate
        existing.price = component.price
        existing.priceCurrency = component.priceCurrency
        val entity = try {
            repository.saveAndFlush(existing)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_DATA_INVALID,
                "Component references an unknown component type or violates a constraint.", e)
        }
        return mapper.map(entity).copy(status = deriveStatus(entity))
    }

    /**
     * Delete is only for data-entry mistakes: allowed while the component was
     * never mounted and never an assembly member; retirement is the lifecycle
     * end for a used component (spec, domain-model.md §3).
     */
    @Transactional
    fun deleteComponent(componentId: UUID) {
        if (!repository.existsById(componentId)) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND)
        }
        if (repository.wasEverMounted(componentId) || repository.wasEverMember(componentId)) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_IN_USE)
        }
        try {
            repository.deleteById(componentId)
            repository.flush()
        } catch (e: DataIntegrityViolationException) {
            // race with a concurrent mount/membership between the checks and the delete
            throw ServiceException(ErrorCodesDomain.COMPONENT_IN_USE)
        }
    }

    /**
     * retire(component, at, scrapped|sold) - domain-model.md §4: requires no
     * active Mounting and no active AssemblyMembership.
     */
    @Transactional
    fun retireComponent(componentId: UUID, at: Instant, kind: DomainRetirementKind): DomainComponent {
        val existing = repository.findById(componentId)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_NOT_FOUND) }
        if (existing.retiredAt != null) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_RETIRED, "Component is already retired.")
        }
        if (repository.hasActiveMounting(componentId)) {
            throw ServiceException(ErrorCodesDomain.RETIRE_PRECONDITION_FAILED,
                "Component has an active mounting; dismount it first.")
        }
        if (repository.hasActiveMembership(componentId)) {
            throw ServiceException(ErrorCodesDomain.RETIRE_PRECONDITION_FAILED,
                "Component is an active assembly member; remove the membership first.")
        }
        existing.retiredAt = at
        existing.retirementKind = kind.name.lowercase()
        val entity = repository.saveAndFlush(existing)
        return mapper.map(entity).copy(status = DomainComponentStatus.RETIRED)
    }

    /** The display identity must not be blank (successor of the old part rule). */
    private fun requireIdentifiable(component: DomainComponent) {
        if (component.label.isBlank()) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_DATA_INVALID, "Label must not be blank.")
        }
    }

    private fun requirePricePair(component: DomainComponent) {
        val hasPrice = !component.price.isNullOrBlank()
        val hasCurrency = !component.priceCurrency.isNullOrBlank()
        if (hasPrice != hasCurrency) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_PRICE_CURRENCY_MISMATCH)
        }
    }

    private fun deriveStatus(entity: ComponentEntity): DomainComponentStatus = statusOf(
        retired = entity.retiredAt != null,
        mounted = repository.hasActiveMounting(entity.id!!),
        member = repository.hasActiveMembership(entity.id!!)
    )

    private fun deriveStatus(
        entity: ComponentEntity,
        activelyMounted: Set<UUID>,
        activeMembers: Set<UUID>,
    ): DomainComponentStatus = statusOf(
        retired = entity.retiredAt != null,
        mounted = entity.id in activelyMounted,
        member = entity.id in activeMembers
    )

    /** Single source of the status precedence (domain-model.md §3). */
    internal fun statusOf(retired: Boolean, mounted: Boolean, member: Boolean): DomainComponentStatus = when {
        retired -> DomainComponentStatus.RETIRED
        mounted -> DomainComponentStatus.MOUNTED
        member -> DomainComponentStatus.IN_ASSEMBLY
        else -> DomainComponentStatus.IN_STOCK
    }
}
