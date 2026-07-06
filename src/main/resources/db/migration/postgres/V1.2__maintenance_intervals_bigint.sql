-- CE-0088: intervals are stored as plain Long everywhere else in the codebase
-- (e.g. tour.duration_moving); keeping distance_interval as numeric and
-- time_interval as PG's `interval` type re-introduces the "no default
-- Hibernate mapping" wrinkle flagged in CE-0080. No data to convert (the
-- migration tool leaves time_interval NULL); the USING clause is harmless.
ALTER TABLE maintenance_task ALTER COLUMN distance_interval TYPE bigint;
ALTER TABLE maintenance_task ALTER COLUMN time_interval TYPE bigint
    USING EXTRACT(EPOCH FROM time_interval)::bigint;
