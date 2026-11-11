CREATE TABLE node_event_priorities (
  priority_id      BIGINT        NOT NULL,
  path             VARCHAR(1024) NOT NULL,
  type             VARCHAR(1024) NOT NULL,
  operator_id      VARCHAR(255)  NOT NULL,
  priority         SMALLINT      NOT NULL,
  timestamp        BIGINT        NOT NULL,
  retry_count      SMALLINT      NOT NULL,
  CONSTRAINT priority_id_pk PRIMARY KEY (priority_id)
);