package de.cyclingsir.cetrack.assembly.domain

import de.cyclingsir.cetrack.assembly.storage.AssemblyDomain2StorageMapper
import de.cyclingsir.cetrack.assembly.storage.AssemblyMembershipRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblyMountingRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblyRepository
import de.cyclingsir.cetrack.assembly.storage.AssemblySlotEntity
import de.cyclingsir.cetrack.assembly.storage.AssemblySlotRepository
import de.cyclingsir.cetrack.assembly.storage.ComponentAssemblyEntity
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * ComponentAssembly CRUD + slots (domain-model.md §3/§4): a named group of
 * AssemblySlots, each with the member active at the requested time. Mounting
 * operations (planMount/mountAssembly/dismountAssembly/add|removeMember) live
 * in AssemblyMountingService (CE-0086 W3 - one place owns governed mountings).
 */
@Service
class AssemblyService(
    private val assemblyRepository: AssemblyRepository,
    private val slotRepository: AssemblySlotRepository,
    private val assemblyMountingRepository: AssemblyMountingRepository,
    private val membershipRepository: AssemblyMembershipRepository,
    private val mapper: AssemblyDomain2StorageMapper,
) {

    @Transactional(readOnly = true)
    fun getAssemblies(): List<DomainAssembly> {
        val now = Instant.now()
        return assemblyRepository.findAll().map { toDomain(it, now) }
    }

    @Transactional(readOnly = true)
    fun getAssembly(assemblyId: UUID, at: Instant): DomainAssembly = toDomain(requireAssembly(assemblyId), at)

    @Transactional
    fun createAssembly(assembly: DomainAssembly): DomainAssembly {
        requireIdentifiable(assembly)
        val entity = try {
            assemblyRepository.saveAndFlush(mapper.map(assembly))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_DATA_INVALID,
                "Assembly references an unknown position.", e)
        }
        return toDomain(entity, Instant.now())
    }

    @Transactional
    fun modifyAssembly(assemblyId: UUID, assembly: DomainAssembly): DomainAssembly {
        val existing = requireAssembly(assemblyId)
        requireIdentifiable(assembly)
        existing.name = assembly.name
        existing.positionId = assembly.positionId
        val entity = try {
            assemblyRepository.saveAndFlush(existing)
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_DATA_INVALID,
                "Assembly references an unknown position.", e)
        }
        return toDomain(entity, Instant.now())
    }

    /** Rejected while membership or mounting history exists (spec) - never deleted, only ended. */
    @Transactional
    fun deleteAssembly(assemblyId: UUID) {
        requireAssembly(assemblyId)
        if (assemblyRepository.hasMembershipHistory(assemblyId)
            || assemblyMountingRepository.existsByAssemblyId(assemblyId)
        ) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_IN_USE)
        }
        try {
            assemblyRepository.deleteById(assemblyId)
            assemblyRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // race with a concurrent membership/mounting between the checks and the delete
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_IN_USE)
        }
    }

    @Transactional
    fun createAssemblySlot(assemblyId: UUID, slot: DomainAssemblySlot): DomainAssemblySlot {
        requireAssembly(assemblyId)
        val entity = AssemblySlotEntity(
            id = null,
            assemblyId = assemblyId,
            componentTypeId = slot.componentTypeId,
            name = slot.name,
            validFrom = slot.validFrom,
            validTo = slot.validTo
        )
        return try {
            mapper.map(slotRepository.saveAndFlush(entity))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_DATA_INVALID,
                "Slot references an unknown component type.", e)
        }
    }

    @Transactional
    fun modifyAssemblySlot(assemblyId: UUID, slotId: UUID, slot: DomainAssemblySlot): DomainAssemblySlot {
        val existing = slotRepository.findByIdAndAssemblyId(slotId, assemblyId)
            ?: throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_NOT_FOUND)
        existing.componentTypeId = slot.componentTypeId
        existing.name = slot.name
        existing.validFrom = slot.validFrom
        existing.validTo = slot.validTo
        return try {
            mapper.map(slotRepository.saveAndFlush(existing))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_DATA_INVALID,
                "Slot references an unknown component type.", e)
        }
    }

    /** Rejected while membership history or slot mappings reference it - end its validity interval instead. */
    @Transactional
    fun deleteAssemblySlot(assemblyId: UUID, slotId: UUID) {
        val existing = slotRepository.findByIdAndAssemblyId(slotId, assemblyId)
            ?: throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_NOT_FOUND)
        if (slotRepository.hasMembershipHistory(slotId) || slotRepository.hasSlotMappings(slotId)) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_IN_USE)
        }
        try {
            slotRepository.delete(existing)
            slotRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_SLOT_IN_USE)
        }
    }

    @Transactional(readOnly = true)
    fun getAssemblyMountings(assemblyId: UUID): List<DomainAssemblyMounting> {
        requireAssembly(assemblyId)
        return assemblyMountingRepository.findAllByAssemblyIdOrderByMountedAt(assemblyId).map(mapper::map)
    }

    /** At least one of slotId/componentId required - avoids full-table dumps (spec). */
    @Transactional(readOnly = true)
    fun getMemberships(slotId: UUID?, componentId: UUID?, activeAt: Instant?): List<DomainAssemblyMembership> {
        if (slotId == null && componentId == null) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_MEMBERSHIP_FILTER_REQUIRED)
        }
        return membershipRepository.findWithAssembly(slotId, componentId, activeAt).map {
            DomainAssemblyMembership(
                id = it.id,
                componentId = it.componentId,
                assemblySlotId = it.assemblySlotId,
                assemblyId = it.assemblyId,
                memberFrom = it.memberFrom,
                memberTo = it.memberTo,
                createdAt = it.createdAt
            )
        }
    }

    private fun requireAssembly(assemblyId: UUID): ComponentAssemblyEntity =
        assemblyRepository.findById(assemblyId)
            .orElseThrow { ServiceException(ErrorCodesDomain.ASSEMBLY_NOT_FOUND) }

    private fun requireIdentifiable(assembly: DomainAssembly) {
        if (assembly.name.isBlank()) {
            throw ServiceException(ErrorCodesDomain.ASSEMBLY_DATA_INVALID, "Name must not be blank.")
        }
    }

    /** every active slot has an active member (vacuously true when none are active - ruling 13) */
    private fun toDomain(entity: ComponentAssemblyEntity, at: Instant): DomainAssembly {
        val assemblyId = entity.id!!
        val slots = slotRepository.findActiveWithMemberAtTime(assemblyId, at).map {
            DomainAssemblySlot(
                id = it.slotId,
                assemblyId = assemblyId,
                name = it.name,
                componentTypeId = it.componentTypeId,
                validFrom = it.validFrom,
                validTo = it.validTo,
                memberComponentId = it.memberComponentId,
                memberFrom = it.memberFrom,
                createdAt = it.createdAt
            )
        }
        val complete = slots.all { it.memberComponentId != null }
        val mounted = assemblyMountingRepository.findByAssemblyIdAndDismountedAtIsNull(assemblyId) != null
        return mapper.map(entity).copy(complete = complete, mounted = mounted, slots = slots)
    }
}
