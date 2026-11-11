ALTER TABLE repository_config ADD COLUMN IF NOT EXISTS repo_type_v2 varchar(32) DEFAULT NULL;

UPDATE repository_config SET repo_type_v2 = CASE WHEN type = 'federated' THEN 'local' ELSE type END WHERE repo_type_v2 IS NULL;
