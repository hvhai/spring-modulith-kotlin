-- `spring-modulith-kotlin`.fruit_order_order definition

CREATE TABLE `fruit_order_order`
(
    `total_amount` decimal(38, 2) DEFAULT NULL,
    `id`           varchar(255) NOT NULL,
    `order_status` enum('CANCELED','CANCELING','DONE','IN_PAYMENT_REQUESTED','IN_PRODUCT_PREPARE','PENDING','WAITING_FOR_PURCHASE') DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- `spring-modulith-kotlin`.fruit_order_payment definition

CREATE TABLE `fruit_order_payment`
(
    `total_amount` decimal(38, 2) DEFAULT NULL,
    `purchase_at`  datetime(6) DEFAULT NULL,
    `id`           varchar(255) NOT NULL,
    `order_id`     varchar(255)   DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKodo5imlw6ckhs37eb1fhnc003` (`order_id`),
    CONSTRAINT `FKtfbomhtk7bhm7uyt40y2pkfns` FOREIGN KEY (`order_id`) REFERENCES `fruit_order_order` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- `spring-modulith-kotlin`.fruit_order_product definition

CREATE TABLE `fruit_order_product`
(
    `price` decimal(38, 2) DEFAULT NULL,
    `id`    varchar(255) NOT NULL,
    `name`  varchar(255)   DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- `spring-modulith-kotlin`.fruit_order_order_product definition

CREATE TABLE `fruit_order_order_product`
(
    `order_id`   varchar(255) NOT NULL,
    `product_id` varchar(255) NOT NULL,
    PRIMARY KEY (`order_id`, `product_id`),
    KEY          `FKighelmqxc93m1lqi79mrc1c47` (`product_id`),
    CONSTRAINT `FK295wrpxs5pl3dyo7xg89n3r1b` FOREIGN KEY (`order_id`) REFERENCES `fruit_order_order` (`id`),
    CONSTRAINT `FKighelmqxc93m1lqi79mrc1c47` FOREIGN KEY (`product_id`) REFERENCES `fruit_order_product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- `spring-modulith-kotlin`.fruit_payment_payment definition

CREATE TABLE `fruit_payment_payment`
(
    `total_amount` decimal(38, 2) DEFAULT NULL,
    `purchase_at`  datetime(6) DEFAULT NULL,
    `id`           varchar(255) NOT NULL,
    `order_id`     varchar(255)   DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- `spring-modulith-kotlin`.fruit_warehouse_product definition

CREATE TABLE `fruit_warehouse_product`
(
    `price`    decimal(38, 2) DEFAULT NULL,
    `quantity` int          NOT NULL,
    `id`       varchar(255) NOT NULL,
    `name`     varchar(255)   DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
