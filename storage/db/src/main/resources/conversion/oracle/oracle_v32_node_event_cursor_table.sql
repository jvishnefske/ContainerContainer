CREATE TABLE node_event_cursor (
  operator_id       VARCHAR2(255)       NOT NULL,
  event_marker      NUMBER(19, 0)       NOT NULL,
  CONSTRAINT operator_id_pk PRIMARY KEY (operator_id)
);