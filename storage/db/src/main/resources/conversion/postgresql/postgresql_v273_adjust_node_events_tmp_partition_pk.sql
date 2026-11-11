Begin;
set statement_timeout = 120000;

-- The original layout had a single-column PK on (tmp_event_id) plus a redundant unique index on
-- (timestamp, tmp_event_id). The shift-events DELETE now filters on the partition-key expression
-- together with tmp_event_id, so the partition PK is widened to (timestamp, tmp_event_id) and the
-- now-redundant unique index is dropped.
DROP INDEX IF EXISTS node_events_tmp_p0_5_idx_time_id;
DROP INDEX IF EXISTS node_events_tmp_p6_11_idx_time_id;
DROP INDEX IF EXISTS node_events_tmp_p12_17_idx_time_id;
DROP INDEX IF EXISTS node_events_tmp_p18_23_idx_time_id;

ALTER TABLE node_events_tmp_p0_5 DROP CONSTRAINT IF EXISTS node_events_tmp_partition_0_5_pk;
ALTER TABLE node_events_tmp_p6_11 DROP CONSTRAINT IF EXISTS node_events_tmp_partition_6_11_pk;
ALTER TABLE node_events_tmp_p12_17 DROP CONSTRAINT IF EXISTS node_events_tmp_partition_12_17_pk;
ALTER TABLE node_events_tmp_p18_23 DROP CONSTRAINT IF EXISTS node_events_tmp_partition_18_23_pk;

ALTER TABLE node_events_tmp_p0_5
    ADD CONSTRAINT node_events_tmp_partition_0_5_pk PRIMARY KEY (timestamp, tmp_event_id);
ALTER TABLE node_events_tmp_p6_11
    ADD CONSTRAINT node_events_tmp_partition_6_11_pk PRIMARY KEY (timestamp, tmp_event_id);
ALTER TABLE node_events_tmp_p12_17
    ADD CONSTRAINT node_events_tmp_partition_12_17_pk PRIMARY KEY (timestamp, tmp_event_id);
ALTER TABLE node_events_tmp_p18_23
    ADD CONSTRAINT node_events_tmp_partition_18_23_pk PRIMARY KEY (timestamp, tmp_event_id);

commit;
