CREATE TABLE quartz_task_history
(
    execution_id       BIGINT       NOT NULL AUTO_INCREMENT,
    external_id        VARCHAR(255),
    task_type          VARCHAR(255) NOT NULL,
    started_date       BIGINT       NOT NULL,
    last_modified_date BIGINT       NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    status_message     VARCHAR(1024),
    deleted            SMALLINT DEFAULT 0,
    task_id            VARCHAR(255) NOT NULL,
    server_id          VARCHAR(64)  NOT NULL,
    CONSTRAINT quartz_task_history_pk PRIMARY KEY (execution_id)
);
CREATE INDEX external_id_idx ON quartz_task_history (external_id);