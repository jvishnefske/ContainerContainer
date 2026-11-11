Begin;
set statement_timeout = 120000;
drop table if exists old_node_events_tmp cascade;

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

DROP INDEX IF EXISTS node_events_tmp_idx_id;
DROP INDEX IF EXISTS node_events_tmp_idx_time_id;
commit;