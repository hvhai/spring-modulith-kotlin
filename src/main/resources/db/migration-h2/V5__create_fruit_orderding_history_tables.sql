create table fruit_warehouse_product_history
(
    id           integer      not null auto_increment,
    product_id   varchar(255) not null,
    old_quantity integer,
    new_quantity integer      not null,
    created_by   varchar(255),
    created_at   timestamp(6) with time zone,
    primary key (id)
);
alter table if exists fruit_warehouse_product_history add constraint FKfruit_warehouse_product foreign key (product_id) references fruit_warehouse_product;
CREATE SEQUENCE fruit_warehouse_product_history_SEQUENCE NO CACHE;