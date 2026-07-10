-- CE-0109: void/correct on an assembly mounting must tell apart mountings it
-- created from pre-existing direct mountings it adopted (assemblyMountingId
-- alone can't distinguish the two - an adopted row's mountedAt may lie
-- either side of the assembly's). Store provenance explicitly.
ALTER TABLE mounting
    ADD COLUMN adopted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mounting
    ADD CONSTRAINT mounting_adopted_requires_governance
    CHECK (adopted = FALSE OR assembly_mounting_id IS NOT NULL);

-- Best-effort backfill (dev data only - no production data predates CE-0091).
-- Misclassifies an adopted row whose mountedAt is >= its assembly's mountedAt
-- (direct mount into an empty governed slot, later adopted via addMember) as
-- created; acceptable here, not relied upon going forward.
UPDATE mounting m
SET adopted = TRUE
WHERE m.assembly_mounting_id IS NOT NULL
  AND m.mounted_at < (
      SELECT am.mounted_at FROM assembly_mounting am WHERE am.id = m.assembly_mounting_id
  );
