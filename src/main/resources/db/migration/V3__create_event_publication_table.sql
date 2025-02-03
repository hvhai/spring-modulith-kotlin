CREATE TABLE `event_publication`
(
    `completion_date`  datetime(6) DEFAULT NULL,
    `publication_date` datetime(6) DEFAULT NULL,
    `id`               binary(16) NOT NULL,
    `event_type`       varchar(255) DEFAULT NULL,
    `listener_id`      varchar(255) DEFAULT NULL,
    `serialized_event` text,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
