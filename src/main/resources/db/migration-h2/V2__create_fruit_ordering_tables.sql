-- `spring-modulith-kotlin`.fruit_order_order definition

create table fruit_order_order
(
    total_amount numeric(38, 2),
    id           varchar(255) not null,
    order_status enum ('CANCELED','CANCELING','DONE','IN_PAYMENT_REQUESTED','IN_PRODUCT_PREPARE','PENDING','WAITING_FOR_PURCHASE'),
    primary key (id)
);
-- `spring-modulith-kotlin`.fruit_order_payment definition
create table fruit_order_payment
(
    total_amount numeric(38, 2),
    purchase_at  timestamp(6) with time zone,
    id           varchar(255) not null,
    order_id     varchar(255) unique,
    primary key (id)
);

-- `spring-modulith-kotlin`.fruit_order_product definition
create table fruit_order_product
(
    price numeric(38, 2),
    id    varchar(255) not null,
    name  varchar(255),
    primary key (id)
);

-- `spring-modulith-kotlin`.fruit_order_order_product definition

create table fruit_order_order_product
(
    order_id   varchar(255) not null,
    product_id varchar(255) not null,
    primary key (order_id, product_id)
);

-- `spring-modulith-kotlin`.fruit_payment_payment definition
create table fruit_payment_payment
(
    total_amount numeric(38, 2),
    purchase_at  timestamp(6) with time zone,
    id           varchar(255) not null,
    order_id     varchar(255),
    primary key (id)
);


-- `spring-modulith-kotlin`.fruit_warehouse_product definition
create table fruit_warehouse_product
(
    price    numeric(38, 2),
    quantity integer      not null,
    id       varchar(255) not null,
    name     varchar(255),
    primary key (id)
);

alter table if exists fruit_order_order_product add constraint FKighelmqxc93m1lqi79mrc1c47 foreign key (product_id) references fruit_order_product;
alter table if exists fruit_order_order_product add constraint FK295wrpxs5pl3dyo7xg89n3r1b foreign key (order_id) references fruit_order_order;
alter table if exists fruit_order_payment add constraint FKtfbomhtk7bhm7uyt40y2pkfns foreign key (order_id) references fruit_order_order;
