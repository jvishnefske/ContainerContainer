CREATE TABLE tasks_tmp (
    task_type    VARCHAR(32)   NOT NULL,
    task_context VARCHAR(2048) NOT NULL,
    created      BIGINT
);
DROP TABLE tasks_tmp;