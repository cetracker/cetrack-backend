-- CUET schema (PostgreSQL 17) - fresh Flyway baseline for the new domain model (CE-0080).
-- Derived 1:1 from CUET_DDD_Model/migration/schema.sql (DROP block removed); keep in sync.

-- needed for the EXCLUDE USING gist constraints below (equality + range overlap in one index)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- Catalog tables
-- ---------------------------------------------------------------------------

CREATE TABLE position (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE component_type (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL UNIQUE,
    description text,
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Bikes & their composition
-- ---------------------------------------------------------------------------
-- NULL allowed for name; not populated on migration
CREATE TABLE bike (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name           text,                 
    manufacturer   text,
    model          text,
    purchase_date  date,
    price          varchar,
    price_currency char(3),
    retired_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE mount_point (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bike_id           uuid NOT NULL REFERENCES bike(id),
    component_type_id uuid NOT NULL REFERENCES component_type(id),
    position_id       uuid REFERENCES position(id),
    name              text NOT NULL,
    mandatory         boolean NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Components
-- ---------------------------------------------------------------------------

CREATE TABLE component (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    component_type_id uuid NOT NULL REFERENCES component_type(id),
    label             text NOT NULL,
    manufacturer      text,
    model             text,
    serial_number     text,
    vendor            text,
    purchase_date     date,
    price             varchar,
    price_currency    char(3),
    retired_at        timestamptz,
    retirement_kind   varchar(20) CHECK (retirement_kind IN ('scrapped', 'sold')),
    created_at        timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Assemblies (created empty — no legacy data, see plan §8 out of scope)
-- ---------------------------------------------------------------------------

CREATE TABLE component_assembly (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id uuid REFERENCES position(id),
    name        text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE assembly_slot (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_id       uuid NOT NULL REFERENCES component_assembly(id),
    component_type_id uuid NOT NULL REFERENCES component_type(id),
    name              text NOT NULL,
    valid_from        timestamptz NOT NULL,
    valid_to          timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE assembly_mounting (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_id    uuid NOT NULL REFERENCES component_assembly(id),
    bike_id        uuid NOT NULL REFERENCES bike(id),
    mounted_at     timestamptz NOT NULL,
    dismounted_at  timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    EXCLUDE USING gist (
        assembly_id WITH =,
        tstzrange(mounted_at, dismounted_at, '[)') WITH &&
    )
);

CREATE UNIQUE INDEX assembly_mounting_active_uq
    ON assembly_mounting (assembly_id) WHERE dismounted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Mounting (direct component -> mount_point, with assembly provenance)
-- ---------------------------------------------------------------------------

CREATE TABLE mounting (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    component_id          uuid NOT NULL REFERENCES component(id),
    mount_point_id        uuid NOT NULL REFERENCES mount_point(id),
    assembly_mounting_id  uuid REFERENCES assembly_mounting(id),
    mounted_at            timestamptz NOT NULL,
    dismounted_at         timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    EXCLUDE USING gist (
        component_id WITH =,
        tstzrange(mounted_at, dismounted_at, '[)') WITH &&
    ),
    EXCLUDE USING gist (
        mount_point_id WITH =,
        tstzrange(mounted_at, dismounted_at, '[)') WITH &&
    )
);

CREATE UNIQUE INDEX mounting_component_active_uq
    ON mounting (component_id) WHERE dismounted_at IS NULL;
CREATE UNIQUE INDEX mounting_mount_point_active_uq
    ON mounting (mount_point_id) WHERE dismounted_at IS NULL;

CREATE TABLE assembly_membership (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    component_id     uuid NOT NULL REFERENCES component(id),
    assembly_slot_id uuid NOT NULL REFERENCES assembly_slot(id),
    member_from      timestamptz NOT NULL,
    member_to        timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    EXCLUDE USING gist (
        component_id WITH =,
        tstzrange(member_from, member_to, '[)') WITH &&
    )
);

CREATE UNIQUE INDEX assembly_membership_active_uq
    ON assembly_membership (component_id) WHERE member_to IS NULL;

CREATE TABLE slot_mapping (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assembly_slot_id uuid NOT NULL REFERENCES assembly_slot(id),
    bike_id          uuid NOT NULL REFERENCES bike(id),
    mount_point_id   uuid NOT NULL REFERENCES mount_point(id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (assembly_slot_id, bike_id)
);

-- ---------------------------------------------------------------------------
-- Tours
-- ---------------------------------------------------------------------------

CREATE TABLE tour (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bike_id            uuid NOT NULL REFERENCES bike(id),
    mt_tour_id         text,             
    title              text,
    source             varchar(20) DEFAULT 'MYTOURBOOK',
    started_at         timestamptz,
    start_year         smallint,
    start_month        smallint,
    start_day          smallint,
    duration_moving    bigint,            
    duration_recorded  bigint,
    duration_elapsed   bigint,
    distance           integer,          
    ascent             integer,         
    descent            integer,        
    power_total        bigint,        
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz
);

-- ---------------------------------------------------------------------------
-- Maintenance
-- ---------------------------------------------------------------------------

CREATE TABLE maintenance_task (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bike_id          uuid NOT NULL REFERENCES bike(id),
    name             text NOT NULL,
    distance_interval numeric,          
    time_interval    interval,
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE maintenance_event (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    maintenance_task_id  uuid NOT NULL REFERENCES maintenance_task(id),
    performed_at         timestamptz NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Infrastructure tables (tour-import bookkeeping) — DDL copied 1:1 from legacy
-- ---------------------------------------------------------------------------

CREATE TABLE import_session (
    id         uuid PRIMARY KEY,
    status     varchar(20),
    db_version integer,
    payload    text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE import_state (
    id                      integer PRIMARY KEY,
    last_db_version         integer,
    updated_at              timestamptz,
    device_time_backfilled  boolean NOT NULL DEFAULT false
);

CREATE TABLE import_ignore (
    id               uuid PRIMARY KEY,
    started_at       timestamptz,
    distance         integer,
    duration_moving  bigint,
    created_at       timestamptz NOT NULL DEFAULT now()
);
