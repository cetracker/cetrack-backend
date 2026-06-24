-- Audit column stamped manually on Replace action only (null = never replaced)
ALTER TABLE tour
    ADD COLUMN updated_at DATETIME(6) NULL;

-- Ignore set for Suppress / Import-as-new auto-suppress.
-- Keyed on triple-signature of the matched existing tour (stable across MT re-imports).
CREATE TABLE import_ignore (
    id              BINARY(16)   NOT NULL,
    started_at      DATETIME     NOT NULL,
    distance        INTEGER      NOT NULL,
    duration_moving BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_import_ignore_triple (started_at, distance, duration_moving)
);
