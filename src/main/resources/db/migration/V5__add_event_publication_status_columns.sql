ALTER TABLE `event_publication`
    ADD COLUMN `status`                 varchar(20) DEFAULT NULL,
    ADD COLUMN `completion_attempts`    int NOT NULL DEFAULT 0,
    ADD COLUMN `last_resubmission_date` datetime(6) DEFAULT NULL;

UPDATE `event_publication`
SET `status` = CASE WHEN `completion_date` IS NULL THEN 'PUBLISHED' ELSE 'COMPLETED' END;
