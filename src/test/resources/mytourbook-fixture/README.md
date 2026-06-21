# MyTourbook Derby Test Fixture

Small anonymized Apache Derby database for CE-0036's embedded Derby adapter tests.
Produced by `build-fixture.sh` from `schema.sql` + `data.sql`; no personal data.

## Contents

| File | Purpose |
|------|---------|
| `schema.sql` | DDL for the 4 tables the export query touches |
| `data.sql` | 30 anonymized TOURDATA rows + tags + DBVERSION |
| `build-fixture.sh` | Builds `tourbook.tar.bz2` from the SQL files |
| `tourbook.tar.bz2` | Pre-built fixture consumed by CE-0036 adapter tests |

## Fixed constants (reuse in CE-0036 tests)

```
bikeA UUID : a1111111-0001-0001-0001-000000000001
bikeB UUID : b2222222-0002-0002-0002-000000000002
bikeC UUID : c3333333-0003-0003-0003-000000000003  (foreign/untracked)
```

**TOURID scheme** (`9000000000001`–`9000000000030`):

| Range | Scenario |
|-------|---------|
| `…001`–`…012` | bikeA candidates (person=0, type IN {0,1,2,4,113}) |
| `…013`–`…024` | bikeB candidates (same filters) |
| `…025` | tagged bikeA **and** bikeB → adapter must classify `AMBIGUOUS_BIKE` |
| `…026` | tagged bikeC (foreign UUID) → excluded by `TT.NAME IN (:bikeUuids)` |
| `…027` | tagged `Road` only (non-UUID) → excluded |
| `…028` | untagged → excluded (no join row) |
| `…029` | bikeA, `TOURPERSON_PERSONID=4` → excluded by person filter |
| `…030` | bikeA, `TOURTYPE_TYPEID=6` → excluded by type filter |

The export query returns **26 rows**: 12 bikeA + 12 bikeB + 2 rows for the ambiguous tour.

## Schema deviations from the real DB

Two intentional changes to simplify the fixture:

1. **`TOURTAG.TAGID`** — real schema uses `GENERATED ALWAYS AS IDENTITY`; fixture uses
   plain `BIGINT NOT NULL` so `data.sql` can insert explicit IDs.
2. **`TOURDATA.SERIEDATA`** — real schema is `BLOB NOT NULL` (GPS track data, ~90 MB in
   production); fixture drops `NOT NULL` and inserts `NULL`. The export query does not
   select this column.

## DBVERSION drift test

`DBVERSION` is set to `59` (current real-DB value). CE-0036's drift test seeds
`import_state.last_db_version` ≠ 59 and asserts a `SCHEMA_DRIFT` warning on staging.

To build a fixture with a different version, edit `data.sql`:
```sql
-- change 59 to any other value, then rebuild:
INSERT INTO "USER"."DBVERSION" (VERSION) VALUES (59);
```
Then re-run `bash build-fixture.sh`.

## Regenerating the fixture

Requirements: Java 11+, Apache Derby 10.x tools (see `DERBY_LIB` in the script).

```bash
cd backend/src/test/resources/mytourbook-fixture
bash build-fixture.sh          # uses /opt/db-derby-10.16.1.1-bin/lib/ by default
# or:
DERBY_LIB=/path/to/derby/lib bash build-fixture.sh
```

Commit the updated `tourbook.tar.bz2` alongside any changed SQL files.

## Archive layout

`tourbook.tar.bz2` unpacks as `tourbook/` at the root (the Derby DB directory directly).

> **Note:** The real production archive (CE-0035) nests as `derby-database/tourbook/`.
> CE-0036's unpack logic should locate the directory containing `service.properties`
> to handle both layouts rather than hard-coding a fixed relative path.

## How the schema was captured

`dblook` was run against an anonymized copy of the real DB
(`issues/CE-0024-files/derby-database/tourbook`) with Derby 10.16:

```bash
java -cp /opt/db-derby-10.16.1.1-bin/lib/derby.jar:... \
  org.apache.derby.tools.dblook \
  -d 'jdbc:derby:/tmp/tourbook-copy;user=user' \
  -t TOURDATA TOURTAG TOURDATA_TOURTAG DBVERSION \
  -o schema-raw.sql
```

The output was post-processed to apply the two deviations above.
The original DB was never modified.
