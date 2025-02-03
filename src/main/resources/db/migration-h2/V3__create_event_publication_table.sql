create table event_publication
(
    completion_date  timestamp(6) with time zone,
    publication_date timestamp(6) with time zone,
    id               uuid not null,
    event_type       varchar(255),
    listener_id      varchar(255),
    serialized_event character varying(25500000),
    primary key (id)
);