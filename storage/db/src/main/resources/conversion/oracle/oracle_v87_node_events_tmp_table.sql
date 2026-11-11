CREATE TABLE node_events_tmp
(
    tmp_event_id NUMBER(19, 0) NOT NULL,
    timestamp    NUMBER(19, 0) NOT NULL,
    event_type   NUMBER(5, 0) NOT NULL,
    path         VARCHAR(1344) NOT NULL,
    hints        VARCHAR(4000)
);