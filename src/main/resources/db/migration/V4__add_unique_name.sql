ALTER TABLE fruit_order_product
    ADD CONSTRAINT uc_fruit_order_product_name UNIQUE (name);

ALTER TABLE fruit_warehouse_product
    ADD CONSTRAINT uc_fruit_warehouse_product_name UNIQUE (name);