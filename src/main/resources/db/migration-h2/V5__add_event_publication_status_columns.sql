alter table event_publication add column status varchar(20);
alter table event_publication add column completion_attempts int default 0 not null;
alter table event_publication add column last_resubmission_date timestamp(6) with time zone;

update event_publication
set status = case when completion_date is null then 'PUBLISHED' else 'COMPLETED' end;
