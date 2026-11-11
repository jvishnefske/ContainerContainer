Begin;
set statement_timeout  = 120000;
lock node_events_tmp;
alter table if exists node_events_tmp rename to old_node_events_tmp;
CREATE TABLE IF NOT EXISTS node_events_tmp
(
    tmp_event_id BIGINT        NOT NULL,
    timestamp    BIGINT        NOT NULL,
    event_type   SMALLINT      NOT NULL,
    path         VARCHAR(1344) NOT NULL,
    hints        VARCHAR(4096)
) PARTITION BY list ((timestamp  % (24 * 3600000) / 3600000));

CREATE TABLE node_events_tmp_p0_5 PARTITION OF node_events_tmp
    for values in (0,1,2,3,4,5);
CREATE TABLE node_events_tmp_p6_11 PARTITION OF node_events_tmp
    for values in (6,7,8,9,10,11);
CREATE TABLE node_events_tmp_p12_17 PARTITION OF node_events_tmp
    for values in (12,13,14,15,16,17);
CREATE TABLE node_events_tmp_p18_23 PARTITION OF node_events_tmp
    for values in (18,19,20,21,22,23);

insert into node_events_tmp
select tmp_event_id, timestamp, event_type, path, hints
from old_node_events_tmp
where timestamp > 1; -- Using this condition to ensure index is used and not seq scan that can be very long
drop table old_node_events_tmp cascade;

CREATE UNIQUE INDEX IF NOT EXISTS node_events_tmp_p0_5_idx_time_id on node_events_tmp_p0_5 (timestamp, tmp_event_id);
CREATE UNIQUE INDEX IF NOT EXISTS node_events_tmp_p6_11_idx_time_id on node_events_tmp_p6_11 (timestamp, tmp_event_id);
CREATE UNIQUE INDEX IF NOT EXISTS node_events_tmp_p12_17_idx_time_id on node_events_tmp_p12_17 (timestamp, tmp_event_id);
CREATE UNIQUE INDEX IF NOT EXISTS node_events_tmp_p18_23_idx_time_id on node_events_tmp_p18_23 (timestamp, tmp_event_id);

ALTER TABLE node_events_tmp_p0_5
    ADD CONSTRAINT node_events_tmp_partition_0_5_pk PRIMARY KEY (tmp_event_id);
ALTER TABLE node_events_tmp_p6_11
    ADD CONSTRAINT node_events_tmp_partition_6_11_pk PRIMARY KEY (tmp_event_id);
ALTER TABLE node_events_tmp_p12_17
    ADD CONSTRAINT node_events_tmp_partition_12_17_pk PRIMARY KEY (tmp_event_id);
ALTER TABLE node_events_tmp_p18_23
    ADD CONSTRAINT node_events_tmp_partition_18_23_pk PRIMARY KEY (tmp_event_id);
commit;