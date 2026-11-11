CREATE TABLE tasks_tmp (
    task_type    VARCHAR(32)   NOT NULL,
    task_context VARCHAR(2048) NOT NULL,
    created      BIGINT
);
INSERT INTO tasks_tmp (task_type, task_context, created) SELECT * FROM tasks WHERE task_type IN ('XRAY_EVENT', 'AIR_GAP_EXPORT', 'DEBIAN_INDEX');
TRUNCATE TABLE tasks;
INSERT INTO tasks SELECT * FROM tasks_tmp;
DROP TABLE tasks_tmp;