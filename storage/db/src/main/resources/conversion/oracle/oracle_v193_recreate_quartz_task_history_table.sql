DROP table quartz_task_history;

CREATE TABLE quartz_task_history
(
    run_id             NUMBER(19, 0) GENERATED ALWAYS AS IDENTITY,
    external_id        VARCHAR(255),
    task_type          VARCHAR(255)  NOT NULL,
    started_date       NUMBER(19, 0),
    finished_date      NUMBER(19, 0),
    last_modified_date NUMBER(19, 0) NOT NULL,
    status             VARCHAR(64)   NOT NULL,
    status_message     VARCHAR(1024),
    deleted            NUMBER(1, 0)  DEFAULT 0,
    task_id            VARCHAR(255)  NOT NULL,
    server_id          VARCHAR(64)   NOT NULL,
    manual_trigger     NUMBER(1, 0)  DEFAULT 0,
    username           VARCHAR(64)   NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (run_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);