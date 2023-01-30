CREATE TABLE IF NOT EXISTS `tour`
(
  `id`              BINARY(16)    NOT NULL,
  `title`           VARCHAR(255)  NOT NULL,
  `length`          LONG          NOT NULL,
  `duration`        LONG          NOT NULL,
  `started_at`      DATETIME      NOT NULL,
  `created_at`      DATETIME(3)   NOT NULL,
  `bike_id`         BINARY(16)    NULL,
  PRIMARY KEY (`id`),
  INDEX `fk_tour_bike_idx` (`bike_id` ASC),
  CONSTRAINT `fk_tour_bike`
      FOREIGN KEY (`bike_id`)
          REFERENCES `bike` (`id`)
          ON DELETE NO ACTION
          ON UPDATE NO ACTION
)
DEFAULT CHARACTER SET = 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci';

