package de.cyclingsir.cetrack.assembly.domain

import de.cyclingsir.cetrack.assembly.storage.AssemblyMembershipEntity
import de.cyclingsir.cetrack.assembly.storage.AssemblyMembershipRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipAction
import de.cyclingsir.cetrack.mounting.domain.DomainMembershipChange
import de.cyclingsir.cetrack.mounting.domain.MembershipPropagation
import de.cyclingsir.cetrack.mounting.storage.MountingEntity
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * ADR-0001 §2 implementation of the mounting/domain port (CE-0086 ruling 5):
 * the replacement enters the occupant's slot; old membership + old governed
 * mounting close at [at].
 */
@Service
class MembershipPropagationImpl(
    private val membershipRepository: AssemblyMembershipRepository,
) : MembershipPropagation {

    override fun propagate(occupant: MountingEntity, replacement: MountingEntity, at: Instant): List<DomainMembershipChange> {
        val oldMembership = membershipRepository.findByComponentIdAndMemberToIsNull(occupant.componentId)
            ?: return emptyList()
        if (membershipRepository.findByComponentIdAndMemberToIsNull(replacement.componentId) != null) {
            // W2 guard: the swap-in must not already be a member elsewhere - a DB constraint
            // violation here would be a confusing 500, not the friendly 409 the user needs
            throw ServiceException(ErrorCodesDomain.ALREADY_MEMBER,
                "Replacement is already an active member of another assembly.")
        }
        replacement.assemblyMountingId = occupant.assemblyMountingId

        oldMembership.memberTo = at
        membershipRepository.saveAndFlush(oldMembership)
        membershipRepository.saveAndFlush(
            AssemblyMembershipEntity(id = null, componentId = replacement.componentId,
                assemblySlotId = oldMembership.assemblySlotId, memberFrom = at)
        )
        return listOf(
            DomainMembershipChange(occupant.componentId, oldMembership.assemblySlotId, DomainMembershipAction.REMOVED, at),
            DomainMembershipChange(replacement.componentId, oldMembership.assemblySlotId, DomainMembershipAction.ADDED, at),
        )
    }
}
