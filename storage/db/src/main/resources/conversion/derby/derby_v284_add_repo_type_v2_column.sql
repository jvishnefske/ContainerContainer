ALTER TABLE repository_config ADD COLUMN repo_type_v2 VARCHAR(32) DEFAULT NULL;

UPDATE repository_config SET repo_type_v2 = CASE WHEN type = 'federated' THEN 'local' ELSE type END WHERE repo_type_v2 IS NULL;
