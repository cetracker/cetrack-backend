package de.cyclingsir.cetrack.assembly.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/** ADR-0003 steps 1-4, pure - every branch without Spring or a database. */
class SlotResolverTest {

    private val front = UUID.randomUUID()
    private val rear = UUID.randomUUID()

    private fun candidate(positionId: UUID? = null) =
        ResolverCandidate(mountPointId = UUID.randomUUID(), mountPointName = "mp", positionId = positionId)

    @Test
    fun `zero candidates is unmountable`() {
        val outcome = SlotResolver.resolveSlot(emptyList(), null, null, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Unmountable(UnmountableReason.NO_CANDIDATE))
    }

    @Test
    fun `single candidate resolves regardless of position mismatch`() {
        val only = candidate(positionId = rear)
        val outcome = SlotResolver.resolveSlot(listOf(only), assemblyPositionId = front, null, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Resolved(only.mountPointId, ResolvedBy.UNIQUE_CANDIDATE))
    }

    @Test
    fun `position filter narrows multiple candidates to one`() {
        val frontMp = candidate(positionId = front)
        val rearMp = candidate(positionId = rear)
        val outcome = SlotResolver.resolveSlot(listOf(frontMp, rearMp), assemblyPositionId = front, null, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Resolved(frontMp.mountPointId, ResolvedBy.POSITION_FILTER))
    }

    @Test
    fun `position filter emptying all candidates is unmountable`() {
        val rearMp1 = candidate(positionId = rear)
        val rearMp2 = candidate(positionId = rear)
        val outcome = SlotResolver.resolveSlot(listOf(rearMp1, rearMp2), assemblyPositionId = front, null, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Unmountable(UnmountableReason.POSITION_FILTER_EMPTY))
    }

    @Test
    fun `stored slot mapping resolves an otherwise-ambiguous slot`() {
        val a = candidate()
        val b = candidate()
        val outcome = SlotResolver.resolveSlot(listOf(a, b), assemblyPositionId = null, storedMappingMountPointId = b.mountPointId, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Resolved(b.mountPointId, ResolvedBy.SLOT_MAPPING))
    }

    @Test
    fun `a stale stored mapping outside the candidate set is never a hit - falls through to unresolved`() {
        val a = candidate()
        val b = candidate()
        val staleMountPointId = UUID.randomUUID()
        val outcome = SlotResolver.resolveSlot(listOf(a, b), assemblyPositionId = null, storedMappingMountPointId = staleMountPointId, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Unresolved(listOf(a, b)))
    }

    @Test
    fun `ask-once user answer resolves an otherwise-ambiguous slot`() {
        val a = candidate()
        val b = candidate()
        val outcome = SlotResolver.resolveSlot(listOf(a, b), assemblyPositionId = null, storedMappingMountPointId = null, userAnswerMountPointId = a.mountPointId)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Resolved(a.mountPointId, ResolvedBy.SLOT_MAPPING))
    }

    @Test
    fun `no stored mapping and no answer stays unresolved with the filtered candidates`() {
        val a = candidate()
        val b = candidate()
        val outcome = SlotResolver.resolveSlot(listOf(a, b), assemblyPositionId = null, null, null)
        assertThat(outcome).isEqualTo(SlotResolutionOutcome.Unresolved(listOf(a, b)))
    }

    @Test
    fun `collision detection flags slots that resolved to the same mount point`() {
        val slot1 = UUID.randomUUID()
        val slot2 = UUID.randomUUID()
        val slot3 = UUID.randomUUID()
        val sharedMountPoint = UUID.randomUUID()
        val otherMountPoint = UUID.randomUUID()

        val colliding = SlotResolver.collidingSlotIds(
            mapOf(slot1 to sharedMountPoint, slot2 to sharedMountPoint, slot3 to otherMountPoint)
        )
        assertThat(colliding).containsExactlyInAnyOrder(slot1, slot2)
    }

    @Test
    fun `no collision when every slot resolves to a distinct mount point`() {
        val slot1 = UUID.randomUUID()
        val slot2 = UUID.randomUUID()
        val colliding = SlotResolver.collidingSlotIds(mapOf(slot1 to UUID.randomUUID(), slot2 to UUID.randomUUID()))
        assertThat(colliding).isEmpty()
    }
}
