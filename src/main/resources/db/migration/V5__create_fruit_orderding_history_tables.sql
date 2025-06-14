create table fruit_warehouse_product_history
(
    id SERIAL PRIMARY KEY,
    product_id   varchar(255) not null,
    old_quantity integer,
    new_quantity integer      not null,
    created_by   varchar(255),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE fruit_warehouse_product_history ADD CONSTRAINT `FKfruit_warehouse_product` FOREIGN KEY (`product_id`) REFERENCES `fruit_warehouse_product` (`id`);
