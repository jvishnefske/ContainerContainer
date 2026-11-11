CREATE TABLE node_events_tmp
(
    tmp_event_id BIGINT        NOT NULL,
    timestamp    BIGINT        NOT NULL,
    event_type   SMALLINT      NOT NULL,
    path         VARCHAR(1344) NOT NULL,
    hints        VARCHAR(4096)
);