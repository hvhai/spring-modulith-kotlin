-- `spring-modulith-kotlin`.todo definition
CREATE TABLE `todo`
(
    `is_done` bit(1)       NOT NULL,
    `id`      varchar(255) NOT NULL,
    `note`    varchar(255) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;