create table if not exists `part_type`
(
  `id`             binary(16)   primary key,
  `name`           VARCHAR(255) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `created_at`     DATETIME(3)  NOT NULL
)
DEFAULT CHARSET = latin1;

CREATE TABLE IF NOT EXISTS `part`
(
  `id`              BINARY(16)    NOT NULL,
  `name`            VARCHAR(255) CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci' NOT NULL,
  `created_at`      DATETIME(3)   NOT NULL,
  `first_used_date` DATETIME      NULL,
  `retired_date`    DATETIME      NULL,
  PRIMARY KEY (`id`)
/*
  INDEX `fk_part_part_type_idx` (`part_type_id` ASC)
  CONSTRAINT `fk_part_part_type1`
    FOREIGN KEY (`part_type_id`)
    REFERENCES `part_type` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION
 */
)
DEFAULT CHARACTER SET = latin1;

CREATE TABLE IF NOT EXISTS `part_part_types`
(
  `part_id`       BINARY(16) NOT NULL,
  `part_type_id`  BINARY(16) NOT NULL,
  `valid_from`    DATETIME NOT NULL,
  `valid_until`   DATETIME NULL,
  PRIMARY KEY (`part_id`, `part_type_id`, `valid_from`),
  CONSTRAINT `ppt_fk_part_id`
    FOREIGN KEY (`part_id`)
    REFERENCES `part` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `ppt_fk_part_type_id`
    FOREIGN KEY (`part_type_id`)
    REFERENCES `part_type` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `ppt_chk_from_before_until`
    CHECK ( `valid_from` <= `valid_until`)
)
DEFAULT CHARACTER SET = latin1;
