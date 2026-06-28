ALTER TABLE tour
    ADD COLUMN duration_recorded BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN duration_elapsed  BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tour
    DROP INDEX uq_tour_started_distance_duration;

ALTER TABLE tour
    ADD CONSTRAINT uq_tour_started_distance_duration
    UNIQUE (started_at, distance, duration_recorded, duration_elapsed, bike_id);

ALTER TABLE import_state
    ADD COLUMN device_time_backfilled TINYINT(1) NOT NULL DEFAULT 0;
