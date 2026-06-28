ALTER TABLE tour DROP INDEX uq_tour_mt_tour_id;

CREATE INDEX idx_tour_mt_tour_id ON tour (mt_tour_id);

ALTER TABLE tour ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MYTOURBOOK';
