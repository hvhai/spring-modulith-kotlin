-- `spring-modulith-kotlin`.todo definition
create table todo
(
    is_done boolean      not null,
    id      varchar(255) not null,
    note    varchar(255),
    primary key (id)
);