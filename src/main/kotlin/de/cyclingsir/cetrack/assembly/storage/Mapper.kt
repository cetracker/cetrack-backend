package de.cyclingsir.cetrack.assembly.storage

import de.cyclingsir.cetrack.assembly.domain.DomainAssembly
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblyMounting
import de.cyclingsir.cetrack.assembly.domain.DomainAssemblySlot

class AssemblyDomain2StorageMapper {

    fun map(domain: DomainAssembly): ComponentAssemblyEntity = ComponentAssemblyEntity(
        id = domain.id,
        positionId = domain.positionId,
        name = domain.name,
        createdAt = domain.createdAt
    )

    /** Bare read view (no slots) - the service assembles slots/complete/mounted separately. */
    fun map(jpa: ComponentAssemblyEntity): DomainAssembly = DomainAssembly(
        id = jpa.id,
        name = jpa.name,
        positionId = jpa.positionId,
        createdAt = jpa.createdAt
    )

    fun map(domain: DomainAssemblySlot): AssemblySlotEntity = AssemblySlotEntity(
        id = domain.id,
        assemblyId = domain.assemblyId,
        componentTypeId = domain.componentTypeId,
        name = domain.name,
        validFrom = domain.validFrom,
        validTo = domain.validTo,
        createdAt = domain.createdAt
    )

    fun map(jpa: AssemblySlotEntity): DomainAssemblySlot = DomainAssemblySlot(
        id = jpa.id,
        assemblyId = jpa.assemblyId,
        componentTypeId = jpa.componentTypeId,
        name = jpa.name,
        validFrom = jpa.validFrom,
        validTo = jpa.validTo,
        createdAt = jpa.createdAt
    )

    fun map(jpa: AssemblyMountingEntity): DomainAssemblyMounting = DomainAssemblyMounting(
        id = jpa.id,
        assemblyId = jpa.assemblyId,
        bikeId = jpa.bikeId,
        mountedAt = jpa.mountedAt,
        dismountedAt = jpa.dismountedAt,
        createdAt = jpa.createdAt
    )
}
