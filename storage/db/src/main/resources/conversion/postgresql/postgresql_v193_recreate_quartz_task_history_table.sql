DROP table quartz_task_history;

CREATE TABLE quartz_task_history
(
    run_id             BIGSERIAL,
    external_id        VARCHAR(255),
    task_type          VARCHAR(255) NOT NULL,
    started_date       BIGINT,
    finished_date      BIGINT,
    last_modified_date BIGINT       NOT NULL,
    status             VARCHAR(64)  NOT NULL,
    status_message     VARCHAR(1024),
    deleted            SMALLINT DEFAULT 0,
    task_id            VARCHAR(255) NOT NULL,
    server_id          VARCHAR(64)  NOT NULL,
    manual_trigger     SMALLINT DEFAULT 0,
    username           VARCHAR(64)  NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (run_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);