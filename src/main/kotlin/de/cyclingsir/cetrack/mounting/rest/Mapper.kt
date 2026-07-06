package de.cyclingsir.cetrack.mounting.rest

import de.cyclingsir.cetrack.infrastructure.api.model.MembershipChange
import de.cyclingsir.cetrack.infrastructure.api.model.Mounting
import de.cyclingsir.cetrack.infrastructure.api.model.MountingChanges
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipAction
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipChange
import de.cyclingsir.cetrack.mounting.domain.DomainMounting
import de.cyclingsir.cetrack.mounting.domain.DomainMountingChanges
import java.time.ZoneOffset

class MountingDomain2ApiMapper {

    fun map(domain: DomainMounting): Mounting = Mounting(
        id = domain.id,
        componentId = domain.componentId,
        mountPointId = domain.mountPointId,
        bikeId = domain.bikeId,
        mountPointName = domain.mountPointName,
        assemblyMountingId = domain.assemblyMountingId,
        mountedAt = domain.mountedAt.atOffset(ZoneOffset.UTC),
        dismountedAt = domain.dismountedAt?.atOffset(ZoneOffset.UTC),
        createdAt = domain.createdAt?.atOffset(ZoneOffset.UTC)
    )

    fun map(domain: DomainMembershipChange): MembershipChange = MembershipChange(
        componentId = domain.componentId,
        assemblySlotId = domain.assemblySlotId,
        action = when (domain.action) {
            DomainMembershipAction.ADDED -> MembershipChange.Action.added
            DomainMembershipAction.REMOVED -> MembershipChange.Action.removed
        },
        at = domain.at.atOffset(ZoneOffset.UTC)
    )

    fun map(domain: DomainMountingChanges): MountingChanges = MountingChanges(
        created = domain.created.map(::map),
        closed = domain.closed.map(::map),
        membershipChanges = domain.membershipChanges.map(::map)
    )
}
