CREATE TABLE node_event_cursor (
  operator_id      VARCHAR(255) NOT NULL,
  event_marker     BIGINT       NOT NULL,
  CONSTRAINT operator_id_pk PRIMARY KEY (operator_id)
);