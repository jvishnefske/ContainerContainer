SELECT * INTO tasks_tmp FROM tasks WHERE task_type IN ('XRAY_EVENT', 'AIR_GAP_EXPORT', 'DEBIAN_INDEX');
TRUNCATE TABLE tasks;
INSERT INTO tasks SELECT * FROM tasks_tmp;
DROP TABLE tasks_tmp;