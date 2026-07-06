-- CE-0086: assembly_membership only guarded ≤1-active-per-component (V1.0). A
-- slot must also hold ≤1 active member, and no two members may overlap
-- historically in the same slot ("composition at time X" - domain-model.md §5).
-- The gist form (not a partial-unique index) enforces both current and
-- historical non-overlap in one constraint.
ALTER TABLE assembly_membership
    ADD CONSTRAINT assembly_membership_slot_occupancy_excl
    EXCLUDE USING gist (
        assembly_slot_id WITH =,
        tstzrange(member_from, member_to, '[)') WITH &&
    );
