CREATE TABLE quartz_task_history
(
    execution_id       NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    external_id        VARCHAR(255),
    task_type          VARCHAR(255)  NOT NULL,
    started_date       NUMBER(19, 0) NOT NULL,
    last_modified_date NUMBER(19, 0) NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    status_message     VARCHAR(1024),
    deleted            NUMBER(1, 0)  DEFAULT 0,
    task_id            VARCHAR(255)  NOT NULL,
    server_id          VARCHAR(64)   NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (execution_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);