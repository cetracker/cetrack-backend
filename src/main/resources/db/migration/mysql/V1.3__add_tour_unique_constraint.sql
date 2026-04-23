ALTER TABLE tour
    ADD CONSTRAINT uq_tour_started_distance_duration
    UNIQUE (started_at, distance, duration_moving);
