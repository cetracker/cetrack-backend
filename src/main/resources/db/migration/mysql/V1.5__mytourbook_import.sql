-- Pre-check before applying in production:
--   SELECT mt_tour_id, COUNT(*) FROM tour GROUP BY mt_tour_id HAVING COUNT(*) > 1;
-- If any duplicates appear, resolve them before running this migration.
ALTER TABLE tour
    ADD CONSTRAINT uq_tour_mt_tour_id
    UNIQUE (mt_tour_id);

CREATE TABLE import_session (
    id            BINARY(16)   NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    db_version    INT          NOT NULL,
    payload       LONGTEXT     NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE import_state (
    id               INT         NOT NULL,
    last_db_version  INT         NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

-- Singleton row; locked via SELECT ... FOR UPDATE by the stage tx.
INSERT INTO import_state (id, last_db_version, updated_at)
VALUES (1, 0, NOW(6));
