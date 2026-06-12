-- CE-0003: give parts structured attributes instead of an overloaded name.
-- Rename `name` -> `label` (now optional) and add structured fields so that
-- serial number, make/model, vendor and price no longer have to live in the name.
--
-- Collation is restated explicitly on every column: the `part` table's DEFAULT
-- CHARACTER SET is latin1 (see V1.0__Init), so omitting it on CHANGE/ADD would
-- silently fall back to latin1 and corrupt existing utf8mb4 (German) text.

ALTER TABLE `part`
  CHANGE COLUMN `name` `label` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL;

ALTER TABLE `part`
  ADD COLUMN `manufacturer`            VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  ADD COLUMN `model`                   VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  ADD COLUMN `serial_number`           VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  ADD COLUMN `vendor`                  VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  -- single-currency, display-only amount as a decimal string (e.g. '10.57');
  -- no cost analytics this phase (deferred to a future TCO/reporting issue)
  ADD COLUMN `purchase_price`          VARCHAR(30)  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  ADD COLUMN `purchase_price_currency` VARCHAR(3)   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL;

-- Note: `first_used_date` already exists on `part` (V1.0__Init) and is only now
-- being mapped in the entity; no column change is required for it here.
