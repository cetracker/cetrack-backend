CREATE TABLE IF NOT EXISTS `bike`
(
  `id`              BINARY(16)    NOT NULL,
  `manufacturer`    VARCHAR(255)  NOT NULL,
  `model`           VARCHAR(255)  NOT NULL,
  `bought_at`       DATETIME(3)   NULL,
  `retired_at`      DATETIME(3)   NULL,
  `created_at`      DATETIME(3)   NOT NULL,
  PRIMARY KEY (`id`)
)
DEFAULT CHARACTER SET = 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci';

ALTER TABLE `part_type` ADD
    CONSTRAINT `pt_fk_bike_id`
        FOREIGN KEY (`bike_id`)
        REFERENCES `bike`(`id`)
        ON DELETE NO ACTION
        ON UPDATE NO ACTION;
