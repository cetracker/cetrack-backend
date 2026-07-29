-- CE-0126: retirement reasons widen beyond scrapped/sold and extend to bikes.
ALTER TABLE component DROP CONSTRAINT component_retirement_kind_check;
ALTER TABLE component ADD CONSTRAINT component_retirement_kind_check
    CHECK (retirement_kind IN ('scrapped','sold','gifted','broken','lost','stolen','worn_out','other'));
ALTER TABLE component ADD COLUMN retirement_note varchar(200);

ALTER TABLE bike ADD COLUMN retirement_kind varchar(20);
ALTER TABLE bike ADD COLUMN retirement_note varchar(200);
ALTER TABLE bike ADD CONSTRAINT bike_retirement_kind_check
    CHECK (retirement_kind IS NULL OR retirement_kind IN ('scrapped','sold','gifted','broken','lost','stolen','worn_out','other'));
