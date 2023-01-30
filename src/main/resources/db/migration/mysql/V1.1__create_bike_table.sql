CREATE TABLE IF NOT EXISTS `bike`
(
  `id`              BINARY(16)    NOT NULL,
  `name`            VARCHAR(255)  NOT NULL,
  `created_at`      DATETIME(3)   NOT NULL,
  PRIMARY KEY (`id`)
)
DEFAULT CHARACTER SET = 'utf8mb4' COLLATE 'utf8mb4_0900_ai_ci';

