CREATE TABLE tasks_tmp LIKE tasks;
INSERT INTO tasks_tmp SELECT * FROM tasks WHERE task_type IN ('XRAY_EVENT', 'AIR_GAP_EXPORT', 'DEBIAN_INDEX');
TRUNCATE TABLE tasks;
INSERT INTO tasks SELECT * FROM tasks_tmp;
DROP TABLE tasks_tmp;