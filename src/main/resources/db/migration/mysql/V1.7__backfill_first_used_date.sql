UPDATE part p
SET first_used_date = (
    SELECT MIN(ppt.valid_from)
    FROM part_part_types ppt
    WHERE ppt.part_id = p.id
)
WHERE EXISTS (
    SELECT 1 FROM part_part_types ppt WHERE ppt.part_id = p.id
)
AND (
    p.first_used_date IS NULL
    OR p.first_used_date > (SELECT MIN(ppt.valid_from) FROM part_part_types ppt WHERE ppt.part_id = p.id)
);
